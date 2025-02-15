/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.net.integrationtests

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.INetworkMonitorCallbacks
import android.net.Network
import android.net.metrics.IpConnectivityLog
import android.os.IBinder
import com.android.net.module.util.SharedLog
import com.android.networkstack.netlink.TcpSocketTracker
import com.android.server.NetworkStackService
import com.android.server.NetworkStackService.NetworkMonitorConnector
import com.android.server.NetworkStackService.NetworkStackConnector
import com.android.server.connectivity.NetworkMonitor
import com.android.server.net.integrationtests.NetworkStackInstrumentationService.InstrumentationConnector
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy

private const val TEST_NETID = 42

/**
 * Android service that can return an [android.net.INetworkStackConnector] which can be instrumented
 * through [NetworkStackInstrumentationService].
 * Useful in tests to create test instrumented NetworkStack components that can receive
 * instrumentation commands through [NetworkStackInstrumentationService].
 */
class TestNetworkStackService : Service() {
    override fun onBind(intent: Intent): IBinder = TestNetworkStackConnector(makeTestContext())

    private fun makeTestContext() = spy(applicationContext).also {
        doReturn(mock(IBinder::class.java)).`when`(it).getSystemService(Context.NETD_SERVICE)
    }

    private class TestPermissionChecker : NetworkStackService.PermissionChecker() {
        override fun enforceNetworkStackCallingPermission() = Unit
    }

    private class NetworkMonitorDeps(private val privateDnsBypassNetwork: Network) :
            NetworkMonitor.Dependencies() {
        override fun getPrivateDnsBypassNetwork(network: Network?) = privateDnsBypassNetwork
        override fun onThreadCreated(thread: Thread) =
            InstrumentationConnector.onNetworkMonitorThreadCreated(thread)
        override fun onExecutorServiceCreated(ecs: ExecutorService) =
            InstrumentationConnector.onNetworkMonitorExecutorServiceCreated(ecs)
    }

    /**
     * Mock [HttpURLConnection] to simulate reply from a server.
     */
    private class MockConnection(
        url: URL,
        private val response: HttpResponse
    ) : HttpURLConnection(url) {
        private val responseBytes = checkNotNull(response.content)
            .toByteArray(StandardCharsets.UTF_8)
        override fun getResponseCode() = response.responseCode
        override fun getContentLengthLong() = responseBytes.size.toLong()
        override fun getHeaderField(field: String): String? {
            return when (field) {
                "location" -> response.redirectUrl
                else -> null
            }
        }
        override fun getInputStream() = ByteArrayInputStream(responseBytes)
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }

    private inner class TestNetworkStackConnector(context: Context) : NetworkStackConnector(
            context, TestPermissionChecker(), NetworkStackService.Dependencies()) {

        private val network = Network(TEST_NETID)
        private val privateDnsBypassNetwork = TestNetwork(TEST_NETID)

        private inner class TestNetwork(netId: Int) : Network(netId) {
            override fun openConnection(url: URL) = MockConnection(
                    url, InstrumentationConnector.processRequest(url))
        }

        override fun makeNetworkMonitor(
            network: Network,
            name: String?,
            cb: INetworkMonitorCallbacks
        ) {
            val nm = NetworkMonitor(this@TestNetworkStackService, cb,
                    this.network,
                    mock(IpConnectivityLog::class.java), mock(SharedLog::class.java),
                    mock(NetworkStackService.NetworkStackServiceManager::class.java),
                    NetworkMonitorDeps(privateDnsBypassNetwork),
                    mock(TcpSocketTracker::class.java))
            cb.onNetworkMonitorCreated(NetworkMonitorConnector(nm, TestPermissionChecker()))
        }
    }
}
