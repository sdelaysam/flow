/*
 * Copyright 2013 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package flow;

import android.content.Context;
import android.support.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class FlowTest {
  static class Uno {
  }

  static class Dos {
  }

  static class Tres {
  }

  @NotPersistent static class NoPersist extends TestKey {
    NoPersist() {
      super("NoPersist");
    }
  }

  final TestKey able = new TestKey("Able");
  final TestKey baker = new TestKey("Baker");
  final TestKey charlie = new TestKey("Charlie");
  final TestKey delta = new TestKey("Delta");
  final TestKey noPersist = new NoPersist();

  @Mock KeyManager keyManager;
  @Mock HistoryCallback historyCallback;
  History lastStack;
  Direction lastDirection;

  class FlowDispatcher implements Dispatcher {
    @Override
    public void dispatch(@NonNull Traversal traversal, @NonNull TraversalCallback callback) {
      lastStack = traversal.destination;
      lastDirection = traversal.direction;
      callback.onTraversalCompleted();
    }
  }

  class AsyncDispatcher implements Dispatcher {
    Traversal traversal;
    TraversalCallback callback;

    @Override
    public void dispatch(@NonNull Traversal traversal, @NonNull TraversalCallback callback) {
      this.traversal = traversal;
      this.callback = callback;
      if (traversal.origin != null && traversal.origin.top() == traversal.destination.top()) {
        fire();
      }
    }

    void fire() {
      TraversalCallback oldCallback = callback;
      callback = null;
      traversal = null;
      oldCallback.onTraversalCompleted();
    }

    void assertIdle() {
      assertThat(callback).isNull();
      assertThat(traversal).isNull();
    }

    void assertDispatching(Object newTop) {
      assertThat(callback).isNotNull();
      assertThat(traversal.destination.top()).isEqualTo(newTop);
    }
  }

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void oneTwoThree() {
    History history = History.single(new Uno());
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    flow.setHistoryCallback(historyCallback);

    flow.set(new Dos());
    assertThat(lastStack.top()).isInstanceOf(Dos.class);
    assertThat(lastDirection).isSameAs(Direction.FORWARD);

    flow.set(new Tres());
    assertThat(lastStack.top()).isInstanceOf(Tres.class);
    assertThat(lastDirection).isSameAs(Direction.FORWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isInstanceOf(Dos.class);
    assertThat(lastDirection).isSameAs(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isInstanceOf(Uno.class);
    assertThat(lastDirection).isSameAs(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, times(1)).onHistoryCleared();
  }

  @Test public void historyChangesAfterListenerCall() {
    final History firstHistory = History.single(new Uno());

    class Ourrobouros implements Dispatcher {
      Flow flow = new Flow(keyManager, firstHistory);

      {
        flow.setDispatcher(this);
      }

      @Override
      public void dispatch(@NonNull Traversal traversal, @NonNull TraversalCallback onComplete) {
        assertThat(firstHistory).hasSameSizeAs(flow.getHistory());
        Iterator<Object> original = firstHistory.framesFromTop().iterator();
        for (Object o : flow.getHistory().framesFromTop()) {
          assertThat(o).isEqualTo(original.next());
        }
        onComplete.onTraversalCompleted();
      }
    }

    Ourrobouros listener = new Ourrobouros();
    listener.flow.set(new Dos());
  }

  @Test public void historyPushAllIsPushy() {
    History history =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker, charlie)).build();
    assertThat(history.size()).isEqualTo(3);

    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    flow.setHistoryCallback(historyCallback);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(baker);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(able);

    flow.goBack();
    verify(historyCallback, times(1)).onHistoryCleared();
  }

  @Test public void setHistoryWorks() {
    History history = History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker)).build();
    Flow flow = new Flow(keyManager, history);
    FlowDispatcher dispatcher = new FlowDispatcher();
    flow.setDispatcher(dispatcher);
    flow.setHistoryCallback(historyCallback);

    History newHistory =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(charlie, delta)).build();
    flow.setHistory(newHistory, Direction.FORWARD);
    assertThat(lastDirection).isSameAs(Direction.FORWARD);
    assertThat(lastStack.top()).isSameAs(delta);
    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isSameAs(charlie);
    flow.goBack();
    verify(historyCallback, times(1)).onHistoryCleared();
  }

  @Test public void setObjectGoesBack() {
    History history =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker, charlie, delta)).build();
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    flow.setHistoryCallback(historyCallback);

    assertThat(history.size()).isEqualTo(4);

    flow.set(charlie);
    assertThat(lastStack.top()).isEqualTo(charlie);
    assertThat(lastStack.size()).isEqualTo(3);
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(baker);
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(able);
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, times(1)).onHistoryCleared();
  }

  @Test public void setObjectToMissingObjectPushes() {
    History history = History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker)).build();
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    flow.setHistoryCallback(historyCallback);
    assertThat(history.size()).isEqualTo(2);

    flow.set(charlie);
    assertThat(lastStack.top()).isEqualTo(charlie);
    assertThat(lastStack.size()).isEqualTo(3);
    assertThat(lastDirection).isEqualTo(Direction.FORWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(baker);
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(able);
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, times(1)).onHistoryCleared();
  }

  @Test public void setObjectKeepsOriginal() {
    History history = History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker)).build();
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    assertThat(history.size()).isEqualTo(2);

    flow.set(new TestKey("Able"));
    assertThat(lastStack.top()).isEqualTo(new TestKey("Able"));
    assertThat(lastStack.top() == able).isTrue();
    assertThat(lastStack.top()).isSameAs(able);
    assertThat(lastStack.size()).isEqualTo(1);
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);
  }

  @Test public void replaceHistoryResultsInLengthOneHistory() {
    History history =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker, charlie)).build();
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    assertThat(history.size()).isEqualTo(3);

    flow.replaceHistory(delta, Direction.REPLACE);
    assertThat(lastStack.top()).isEqualTo(new TestKey("Delta"));
    assertThat(lastStack.top() == delta).isTrue();
    assertThat(lastStack.top()).isSameAs(delta);
    assertThat(lastStack.size()).isEqualTo(1);
    assertThat(lastDirection).isEqualTo(Direction.REPLACE);
  }

  @Test public void replaceTopDoesNotAlterHistoryLength() {
    History history =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker, charlie)).build();
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    assertThat(history.size()).isEqualTo(3);

    flow.replaceTop(delta, Direction.REPLACE);
    assertThat(lastStack.top()).isEqualTo(new TestKey("Delta"));
    assertThat(lastStack.top() == delta).isTrue();
    assertThat(lastStack.top()).isSameAs(delta);
    assertThat(lastStack.size()).isEqualTo(3);
    assertThat(lastDirection).isEqualTo(Direction.REPLACE);
  }

  @Test public void secondDispatcherIsBootstrapped() {
    AsyncDispatcher firstDispatcher = new AsyncDispatcher();

    History history = History.single(able);
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(firstDispatcher);

    // Quick check that we bootstrapped (and test the test dispatcher).
    firstDispatcher.assertDispatching(able);
    firstDispatcher.fire();
    firstDispatcher.assertIdle();

    // No activity, dispatchers change. Maybe pause / resume. Maybe config change.
    flow.removeDispatcher(firstDispatcher);
    AsyncDispatcher secondDispatcher = new AsyncDispatcher();
    flow.setDispatcher(secondDispatcher);

    // New dispatcher is bootstrapped
    secondDispatcher.assertDispatching(able);
    secondDispatcher.fire();
    secondDispatcher.assertIdle();
  }

  @Test public void hangingTraversalsSurviveDispatcherChange() {
    AsyncDispatcher firstDispatcher = new AsyncDispatcher();

    History history = History.single(able);
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(firstDispatcher);
    firstDispatcher.fire();

    // Start traversal to second screen.
    flow.set(baker);
    firstDispatcher.assertDispatching(baker);

    // Dispatcher is removed before finishing baker--maybe it caused a configuration change.
    flow.removeDispatcher(firstDispatcher);

    // New dispatcher shows up, maybe from new activity after config change.
    AsyncDispatcher secondDispatcher = new AsyncDispatcher();
    flow.setDispatcher(secondDispatcher);

    // New dispatcher is ignored until the in-progress baker traversal is done.
    secondDispatcher.assertIdle();

    // New dispatcher is bootstrapped with baker.
    firstDispatcher.fire();
    secondDispatcher.assertDispatching(baker);

    // Confirm no redundant extra bootstrap traversals enqueued.
    secondDispatcher.fire();
    secondDispatcher.assertIdle();
  }

  @Test public void enqueuedTraversalsSurviveDispatcherChange() {
    AsyncDispatcher firstDispatcher = new AsyncDispatcher();

    History history = History.single(able);
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(firstDispatcher);
    firstDispatcher.fire();

    // Dispatcher is removed. Maybe we paused.
    flow.removeDispatcher(firstDispatcher);

    // A few traversals are enqueued because software.
    flow.set(baker);
    flow.set(charlie);

    // New dispatcher shows up, we resumed.
    AsyncDispatcher secondDispatcher = new AsyncDispatcher();
    flow.setDispatcher(secondDispatcher);

    // New dispatcher receives baker and charlie traversals and nothing else.
    secondDispatcher.assertDispatching(baker);
    secondDispatcher.fire();
    secondDispatcher.assertDispatching(charlie);
    secondDispatcher.fire();
    secondDispatcher.assertIdle();
  }

  @SuppressWarnings({ "deprecation", "CheckResult" }) @Test public void setHistoryKeepsOriginals() {
    TestKey able = new TestKey("Able");
    TestKey baker = new TestKey("Baker");
    TestKey charlie = new TestKey("Charlie");
    TestKey delta = new TestKey("Delta");
    History history =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker, charlie, delta)).build();
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    assertThat(history.size()).isEqualTo(4);

    TestKey echo = new TestKey("Echo");
    TestKey foxtrot = new TestKey("Foxtrot");
    History newHistory =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, baker, echo, foxtrot)).build();
    flow.setHistory(newHistory, Direction.REPLACE);
    assertThat(lastStack.size()).isEqualTo(4);
    assertThat(lastStack.top()).isEqualTo(foxtrot);
    flow.goBack();
    assertThat(lastStack.size()).isEqualTo(3);
    assertThat(lastStack.top()).isEqualTo(echo);
    flow.goBack();
    assertThat(lastStack.size()).isEqualTo(2);
    assertThat(lastStack.top()).isSameAs(baker);
    flow.goBack();
    assertThat(lastStack.size()).isEqualTo(1);
    assertThat(lastStack.top()).isSameAs(able);
  }

  static class Picky {
    final String value;

    Picky(String value) {
      this.value = value;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Picky picky = (Picky) o;
      return value.equals(picky.value);
    }

    @Override public int hashCode() {
      return value.hashCode();
    }
  }

  @Test public void setCallsEquals() {
    History history = History.emptyBuilder()
        .pushAll(Arrays.<Object>asList(new Picky("Able"), new Picky("Baker"), new Picky("Charlie"),
            new Picky("Delta")))
        .build();
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    flow.setHistoryCallback(historyCallback);

    assertThat(history.size()).isEqualTo(4);

    flow.set(new Picky("Charlie"));
    assertThat(lastStack.top()).isEqualTo(new Picky("Charlie"));
    assertThat(lastStack.size()).isEqualTo(3);
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(new Picky("Baker"));
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, never()).onHistoryCleared();
    assertThat(lastStack.top()).isEqualTo(new Picky("Able"));
    assertThat(lastDirection).isEqualTo(Direction.BACKWARD);

    flow.goBack();
    verify(historyCallback, times(1)).onHistoryCleared();
  }

  @Test public void incorrectFlowGetUsage() {
    Context mockContext = Mockito.mock(Context.class);
    //noinspection WrongConstant
    Mockito.when(mockContext.getSystemService(Mockito.anyString())).thenReturn(null);

    try {
      Flow.get(mockContext);

      fail("Flow was supposed to throw an exception on wrong usage");
    } catch (IllegalStateException ignored) {
      // That's good!
    }
  }

  @Test public void defaultHistoryFilter() {
    History history =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, noPersist, charlie)).build();

    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());

    List<Object> expected = History.emptyBuilder().pushAll(asList(able, charlie)).build().asList();
    assertThat(flow.getFilteredHistory().asList()).isEqualTo(expected);
  }

  @Test public void customHistoryFilter() {
    History history =
        History.emptyBuilder().pushAll(Arrays.<Object>asList(able, noPersist, charlie)).build();

    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(new FlowDispatcher());
    flow.setHistoryFilter(new HistoryFilter() {
      @NonNull @Override public History scrubHistory(@NonNull History history) {
        History.Builder builder = History.emptyBuilder();

        for (Object key : history.framesFromBottom()) {
          if (!key.equals(able)) {
            builder.push(key);
          }
        }

        return builder.build();
      }
    });

    List<Object> expected =
        History.emptyBuilder().pushAll(asList(noPersist, charlie)).build().asList();
    assertThat(flow.getFilteredHistory().asList()).isEqualTo(expected);
  }

  @Test
  public void shouldTerminateInPendingTraversal() {
    AsyncDispatcher dispatcher = Mockito.spy(new AsyncDispatcher());
    History history = History.single(able);
    Flow flow = new Flow(keyManager, history);
    flow.setDispatcher(dispatcher);
    flow.setHistoryCallback(historyCallback);
    dispatcher.fire();

    flow.set(baker);
    dispatcher.fire();
    dispatcher.assertIdle();
    assertThat(flow.getHistory().top()).isEqualTo(baker);
    assertThat(flow.getHistory().size()).isEqualTo(2);

    flow.replaceHistory(History.single(charlie), Direction.REPLACE);
    flow.goBack();

    // check clearHistory not called immediately
    // termination should be called during pending traversal execution
    verify(historyCallback, never()).onHistoryCleared();

    dispatcher.fire();
    verify(historyCallback, times(1)).onHistoryCleared();
//
//    // WELCOME->PROJECTS (replaceTop FORWARD)
//    flow.replaceTop(baker, Direction.FORWARD);
//    // forward to projects list complete
//    dispatcher.fire();
//    dispatcher.assertIdle();
//    assertThat(flow.getHistory().top()).isEqualTo(baker);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
//
//    // PROJECTS->DETAILS (set)
//    flow.set(charlie);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // forward to project details complete
//    dispatcher.fire();
//    dispatcher.assertDispatching(able);
//    assertThat(flow.getHistory().top()).isEqualTo(charlie);
//    assertThat(flow.getHistory().size()).isEqualTo(2);
//
//    // WELCOME->PROJECTS (replaceTop FORWARD)
//    flow.replaceTop(baker, Direction.FORWARD);
//
//    // forward to welcome complete
//    dispatcher.fire();
//    dispatcher.assertDispatching(baker);
//    assertThat(flow.getHistory().top()).isEqualTo(able);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
//
//    // WELCOME->PROJECTS (replaceTop FORWARD)
//    flow.replaceTop(baker, Direction.FORWARD);
//
//    Mockito.reset(dispatcher);
//    // forward to projects list complete
//    dispatcher.fire();
//    verify(dispatcher,  Mockito.times(2)).fire();
//    dispatcher.assertIdle();
//    assertThat(flow.getHistory().top()).isEqualTo(baker);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
//
//    // PROJECTS->DETAILS (set)
//    flow.set(charlie);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // forward to project details complete
//    dispatcher.fire();
//    dispatcher.assertDispatching(able);
//    assertThat(flow.getHistory().top()).isEqualTo(charlie);
//    assertThat(flow.getHistory().size()).isEqualTo(2);
//
//    // WELCOME->PROJECTS (replaceTop FORWARD)
//    flow.replaceTop(baker, Direction.FORWARD);
//
//    Mockito.reset(dispatcher);
//    // forward to welcome complete
//    dispatcher.fire();
//    verify(dispatcher,  Mockito.times(3)).fire();
//    dispatcher.assertDispatching(baker);
//    assertThat(flow.getHistory().top()).isEqualTo(able);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
//
//    // WELCOME->PROJECTS (replaceTop FORWARD)
//    flow.replaceTop(baker, Direction.FORWARD);
//
//    Mockito.reset(dispatcher);
//    // forward to projects list complete
//    dispatcher.fire();
//    verify(dispatcher,  Mockito.times(2)).fire();
//    dispatcher.assertIdle();
//    assertThat(flow.getHistory().top()).isEqualTo(baker);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
//
//    // PROJECTS->DETAILS (set)
//    flow.set(charlie);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // forward to project details complete
//    dispatcher.fire();
//    dispatcher.assertDispatching(able);
//    assertThat(flow.getHistory().top()).isEqualTo(charlie);
//    assertThat(flow.getHistory().size()).isEqualTo(2);
//
//    // WELCOME->PROJECTS (replaceTop FORWARD)
//    flow.replaceTop(baker, Direction.FORWARD);
//
//    Mockito.reset(dispatcher);
//    // forward to welcome complete
//    dispatcher.fire();
//    verify(dispatcher,  Mockito.times(3)).fire();
//    dispatcher.assertDispatching(baker);
//    assertThat(flow.getHistory().top()).isEqualTo(able);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
//
//    // forward to projects list complete
//    dispatcher.fire();
//    dispatcher.assertIdle();
//    assertThat(flow.getHistory().top()).isEqualTo(baker);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
//
//    // GO BACK
//    assertThat(flow.goBack()).isEqualTo(false);
//
//    // PROJECTS->DETAILS (set)
//    flow.set(charlie);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // DETAILS->WELCOME (replaceHistory FORWARD)
//    flow.replaceHistory(able, Direction.FORWARD);
//
//    // forward to project details complete
//    dispatcher.fire();
//    dispatcher.assertDispatching(able);
//    assertThat(flow.getHistory().top()).isEqualTo(charlie);
//    assertThat(flow.getHistory().size()).isEqualTo(2);
//
//    // GO BACK
//    assertThat(flow.goBack()).isEqualTo(true);
//
//    // WELCOME->PROJECTS (replaceTop FORWARD)
//    flow.replaceTop(baker, Direction.FORWARD);
//
//    Mockito.reset(dispatcher);
//    // forward to welcome complete
//    dispatcher.fire();
//    verify(dispatcher,  Mockito.times(3)).fire();
//    dispatcher.assertIdle();
//    assertThat(flow.getHistory().top()).isEqualTo(able);
//    assertThat(flow.getHistory().size()).isEqualTo(1);
  }

//09-12 08:56:10.633 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) 1
//09-12 08:56:10.843 7711-7711/com.sdelaysam.testflow D/WTF: forward to projects list complete 1

//09-12 08:56:11.431 7711-7711/com.sdelaysam.testflow D/WTF: PROJECTS->DETAILS (set) 2
//09-12 08:56:12.108 7711-7711/com.sdelaysam.testflow D/WTF: DETAILS->WELCOME (replaceHistory FORWARD) 3

//09-12 08:56:12.158 7711-7711/com.sdelaysam.testflow D/WTF: forward to project details complete 2
//09-12 08:56:12.675 7711-7711/com.sdelaysam.testflow D/WTF: forward to welcome complete 3

//09-12 08:56:14.487 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) 4
//09-12 08:56:14.705 7711-7711/com.sdelaysam.testflow D/WTF: forward to projects list complete 4

//09-12 08:56:14.705 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) 5
//09-12 08:56:14.903 7711-7711/com.sdelaysam.testflow D/WTF: PROJECTS->DETAILS (set) 6
//09-12 08:56:15.046 7711-7711/com.sdelaysam.testflow D/WTF: GO BACK 7
//09-12 08:56:15.272 7711-7711/com.sdelaysam.testflow D/WTF: DETAILS->WELCOME (replaceHistory FORWARD) 8
//09-12 08:56:15.439 7711-7711/com.sdelaysam.testflow D/WTF: DETAILS->WELCOME (replaceHistory FORWARD) 9

//09-12 08:56:15.621 7711-7711/com.sdelaysam.testflow D/WTF: forward to project details complete 5(auto), 6

//09-12 08:56:15.690 7711-7711/com.sdelaysam.testflow D/WTF: DETAILS->WELCOME (replaceHistory FORWARD) 10
//09-12 08:56:15.855 7711-7711/com.sdelaysam.testflow D/WTF: DETAILS->WELCOME (replaceHistory FORWARD) 11
//09-12 08:56:16.055 7711-7711/com.sdelaysam.testflow D/WTF: DETAILS->WELCOME (replaceHistory FORWARD) 12
//09-12 08:56:16.206 7711-7711/com.sdelaysam.testflow D/WTF: GO BACK 13
//09-12 08:56:16.306 7711-7711/com.sdelaysam.testflow D/WTF: DETAILS->WELCOME (replaceHistory FORWARD) 14

//09-12 08:56:16.340 7711-7711/com.sdelaysam.testflow D/WTF: back to projects list complete 7

//09-12 08:56:16.506 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) 15
//09-12 08:56:16.674 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) 16

//09-12 08:56:16.856 7711-7711/com.sdelaysam.testflow D/WTF: forward to welcome complete 8, 9 - 12

//09-12 08:56:16.874 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) 17
//09-12 08:56:17.029 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) 18
//09-12 08:56:17.112 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD) ...
//09-12 08:56:17.238 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:17.379 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:17.545 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:17.720 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:17.837 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:18.237 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:18.478 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:18.695 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:19.154 7711-7711/com.sdelaysam.testflow D/WTF: GO BACK
//09-12 08:56:19.803 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:20.094 7711-7711/com.sdelaysam.testflow D/WTF: WELCOME->PROJECTS (replaceTop FORWARD)
//09-12 08:56:20.629 7711-7711/com.sdelaysam.testflow D/WTF: GO BACK



}
