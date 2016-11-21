/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.wifi.aware;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import dalvik.system.CloseGuard;

import java.lang.ref.WeakReference;

/**
 * This class represents a Wi-Fi Aware session - an attachment to the Wi-Fi Aware service through
 * which the app can execute discovery operations.
 *
 * @hide PROPOSED_AWARE_API
 */
public class WifiAwareSession {
    private static final String TAG = "WifiAwareSession";
    private static final boolean DBG = false;
    private static final boolean VDBG = false; // STOPSHIP if true

    private final WeakReference<WifiAwareManager> mMgr;
    private final Binder mBinder;
    private final int mClientId;

    private boolean mTerminated = true;
    private final CloseGuard mCloseGuard = CloseGuard.get();

    /** @hide */
    public WifiAwareSession(WifiAwareManager manager, Binder binder, int clientId) {
        if (VDBG) Log.v(TAG, "New session created: manager=" + manager + ", clientId=" + clientId);

        mMgr = new WeakReference<>(manager);
        mBinder = binder;
        mClientId = clientId;
        mTerminated = false;

        mCloseGuard.open("destroy");
    }

    /**
     * Destroy the Wi-Fi Aware service session and, if no other applications are attached to Aware,
     * also disable Aware. This method destroys all outstanding operations - i.e. all publish and
     * subscribes are terminated, and any outstanding data-links are shut-down. However, it is
     * good practice to destroy these discovery sessions and connections explicitly before a
     * session-wide destroy.
     * <p>
     * An application may re-attach after a destroy using
     * {@link WifiAwareManager#attach(WifiAwareAttachCallback, Handler)} .
     */
    public void destroy() {
        WifiAwareManager mgr = mMgr.get();
        if (mgr == null) {
            Log.w(TAG, "destroy: called post GC on WifiAwareManager");
            return;
        }
        mgr.disconnect(mClientId, mBinder);
        mTerminated = true;
        mMgr.clear();
        mCloseGuard.close();
    }

    /** @hide */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!mTerminated) {
                mCloseGuard.warnIfOpen();
                destroy();
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Issue a request to the Aware service to create a new Aware publish discovery session, using
     * the specified {@code publishConfig} configuration. The results of the publish operation
     * are routed to the callbacks of {@link WifiAwareDiscoverySessionCallback}:
     * <ul>
     *     <li>
     *     {@link WifiAwareDiscoverySessionCallback#onPublishStarted(
     *     WifiAwarePublishDiscoverySession)}
     *     is called when the publish session is created and provides a handle to the session.
     *     Further operations on the publish session can be executed on that object.
     *     <li>{@link WifiAwareDiscoverySessionCallback#onSessionConfigFailed()} is called if the
     *     publish operation failed.
     * </ul>
     * <p>
     * Other results of the publish session operations will also be routed to callbacks
     * on the {@code callback} object. The resulting publish session can be modified using
     * {@link WifiAwarePublishDiscoverySession#updatePublish(PublishConfig)}.
     * <p>
     *      An application must use the {@link WifiAwareDiscoveryBaseSession#destroy()} to
     *      terminate the publish discovery session once it isn't needed. This will free
     *      resources as well terminate any on-air transmissions.
     * <p>The application must have the {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}
     * permission to start a publish discovery session.
     *
     * @param publishConfig The {@link PublishConfig} specifying the
     *            configuration of the requested publish session.
     * @param callback A {@link WifiAwareDiscoverySessionCallback} derived object to be used for
     *                 session event callbacks.
     * @param handler The Handler on whose thread to execute the callbacks of the {@code
     * callback} object. If a null is provided then the application's main thread will be used.
     */
    public void publish(@NonNull PublishConfig publishConfig,
            @NonNull WifiAwareDiscoverySessionCallback callback, @Nullable Handler handler) {
        WifiAwareManager mgr = mMgr.get();
        if (mgr == null) {
            Log.e(TAG, "publish: called post GC on WifiAwareManager");
            return;
        }
        if (mTerminated) {
            Log.e(TAG, "publish: called after termination");
            return;
        }
        mgr.publish(mClientId, (handler == null) ? Looper.getMainLooper() : handler.getLooper(),
                publishConfig, callback);
    }

    /**
     * Issue a request to the Aware service to create a new Aware subscribe discovery session, using
     * the specified {@code subscribeConfig} configuration. The results of the subscribe
     * operation are routed to the callbacks of {@link WifiAwareDiscoverySessionCallback}:
     * <ul>
     *     <li>
     *  {@link WifiAwareDiscoverySessionCallback#onSubscribeStarted(
     *  WifiAwareSubscribeDiscoverySession)}
     *     is called when the subscribe session is created and provides a handle to the session.
     *     Further operations on the subscribe session can be executed on that object.
     *     <li>{@link WifiAwareDiscoverySessionCallback#onSessionConfigFailed()} is called if the
     *     subscribe operation failed.
     * </ul>
     * <p>
     * Other results of the subscribe session operations will also be routed to callbacks
     * on the {@code callback} object. The resulting subscribe session can be modified using
     * {@link WifiAwareSubscribeDiscoverySession#updateSubscribe(SubscribeConfig)}.
     * <p>
     *      An application must use the {@link WifiAwareDiscoveryBaseSession#destroy()} to
     *      terminate the subscribe discovery session once it isn't needed. This will free
     *      resources as well terminate any on-air transmissions.
     * <p>The application must have the {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}
     * permission to start a subscribe discovery session.
     *
     * @param subscribeConfig The {@link SubscribeConfig} specifying the
     *            configuration of the requested subscribe session.
     * @param callback A {@link WifiAwareDiscoverySessionCallback} derived object to be used for
     *                 session event callbacks.
     * @param handler The Handler on whose thread to execute the callbacks of the {@code
     * callback} object. If a null is provided then the application's main thread will be used.
     */
    public void subscribe(@NonNull SubscribeConfig subscribeConfig,
            @NonNull WifiAwareDiscoverySessionCallback callback, @Nullable Handler handler) {
        WifiAwareManager mgr = mMgr.get();
        if (mgr == null) {
            Log.e(TAG, "publish: called post GC on WifiAwareManager");
            return;
        }
        if (mTerminated) {
            Log.e(TAG, "publish: called after termination");
            return;
        }
        mgr.subscribe(mClientId, (handler == null) ? Looper.getMainLooper() : handler.getLooper(),
                subscribeConfig, callback);
    }

    /**
     * Create a {@link android.net.NetworkRequest.Builder#setNetworkSpecifier(String)} for a
     * WiFi Aware connection to the specified peer. The
     * {@link android.net.NetworkRequest.Builder#addTransportType(int)} should be set to
     * {@link android.net.NetworkCapabilities#TRANSPORT_WIFI_AWARE}.
     * <p>
     *     This API is targeted for applications which can obtain the peer MAC address using OOB
     *     (out-of-band) discovery. Aware discovery does not provide the MAC address of the peer -
     *     when using Aware discovery use the alternative network specifier method -
     *     {@link WifiAwareDiscoveryBaseSession#createNetworkSpecifier(int,
     *     WifiAwareManager.PeerHandle, byte[])}.
     *
     * @param role  The role of this device:
     *              {@link WifiAwareManager#WIFI_AWARE_DATA_PATH_ROLE_INITIATOR} or
     *              {@link WifiAwareManager#WIFI_AWARE_DATA_PATH_ROLE_RESPONDER}
     * @param peer  The MAC address of the peer's Aware discovery interface. On a RESPONDER this
     *              value is used to gate the acceptance of a connection request from only that
     *              peer. A RESPONDER may specified a null - indicating that it will accept
     *              connection requests from any device.
     * @param token An arbitrary token (message) to be used to match connection initiation request
     *              to a responder setup. A RESPONDER is set up with a {@code token} which must
     *              be matched by the token provided by the INITIATOR. A null token is permitted
     *              on the RESPONDER and matches any peer token. An empty ({@code ""}) token is
     *              not the same as a null token and requires the peer token to be empty as well.
     *
     * @return A string to be used to construct
     * {@link android.net.NetworkRequest.Builder#setNetworkSpecifier(String)} to pass to
     * {@link android.net.ConnectivityManager#requestNetwork(NetworkRequest,
     * android.net.ConnectivityManager.NetworkCallback)}
     * [or other varieties of that API].
     */
    public String createNetworkSpecifier(@WifiAwareManager.DataPathRole int role,
            @Nullable byte[] peer, @Nullable byte[] token) {
        WifiAwareManager mgr = mMgr.get();
        if (mgr == null) {
            Log.e(TAG, "createNetworkSpecifier: called post GC on WifiAwareManager");
            return "";
        }
        if (mTerminated) {
            Log.e(TAG, "createNetworkSpecifier: called after termination");
            return "";
        }
        return mgr.createNetworkSpecifier(mClientId, role, peer, token);
    }
}
