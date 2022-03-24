/*
 * Copyright 2021 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.libraries.testing.deviceshadower.shadows.bluetooth;

import android.os.ParcelFileDescriptor;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Inert implementation of a ParcelFileDescriptor to make bluetooth connections function.
 */
@Implements(ParcelFileDescriptor.class)
public class ShadowParcelFileDescriptor {

    private FileDescriptor mFd;

    public ShadowParcelFileDescriptor() {
    }

    public void __constructor__(FileDescriptor fd) {
        this.mFd = fd;
    }

    @Implementation
    public FileDescriptor getFileDescriptor() {
        return mFd;
    }

    @Implementation
    public void close() throws IOException {
        // Nothing
    }
}
