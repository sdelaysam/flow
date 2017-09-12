/*
 * Copyright 2016 Square Inc.
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

package flow.sample.helloworld;

import android.app.Activity;
import android.content.Context;
import flow.Flow;
import flow.HistoryCallback;

public class HelloWorldActivity extends Activity implements HistoryCallback {

  @Override protected void attachBaseContext(Context baseContext) {
    baseContext = Flow.configure(baseContext, this).historyCallback(this).install();
    super.attachBaseContext(baseContext);
  }

  @Override public void onBackPressed() {
    Flow.get(this).goBack();
  }

  @Override
  public void onHistoryCleared() {
    super.onBackPressed();
  }
}
