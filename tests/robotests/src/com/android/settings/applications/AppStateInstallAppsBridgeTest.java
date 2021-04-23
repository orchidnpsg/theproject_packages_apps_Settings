/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import android.app.AppOpsManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppStateInstallAppsBridgeTest {

    @Test
    public void testInstallAppsStateCanInstallApps() {
        AppStateInstallAppsBridge.InstallAppsState appState =
            new AppStateInstallAppsBridge.InstallAppsState();
        assertThat(appState.canInstallApps()).isFalse();

        appState.permissionRequested = true;
        assertThat(appState.canInstallApps()).isFalse();

        appState.appOpMode = AppOpsManager.MODE_ALLOWED;
        assertThat(appState.canInstallApps()).isTrue();

        appState.appOpMode = AppOpsManager.MODE_ERRORED;
        assertThat(appState.canInstallApps()).isFalse();
    }

    @Test
    public void testInstallAppsStateIsPotentialAppSource() {
        AppStateInstallAppsBridge.InstallAppsState appState =
            new AppStateInstallAppsBridge.InstallAppsState();
        assertThat(appState.isPotentialAppSource()).isFalse();

        appState.appOpMode = AppOpsManager.MODE_ERRORED;
        assertThat(appState.isPotentialAppSource()).isTrue();

        appState.permissionRequested = true;
        appState.appOpMode = AppOpsManager.MODE_DEFAULT;
        assertThat(appState.isPotentialAppSource()).isTrue();
    }
}
