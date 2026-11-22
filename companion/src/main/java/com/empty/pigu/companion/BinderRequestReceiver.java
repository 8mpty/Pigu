package com.empty.pigu.companion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * A package-identity only companion: it never runs a UI or a service. Legacy clients that
 * explicitly target the old Shizuku package can still ask for a binder; forward that request
 * to Pigu, which provides the actual service.
 */
public class BinderRequestReceiver extends BroadcastReceiver {

    private static final String ACTION = "rikka.shizuku.intent.action.REQUEST_BINDER";
    private static final String PIGU_PACKAGE = "com.empty.pigu";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;

        Intent forward = new Intent(intent);
        forward.setPackage(PIGU_PACKAGE);
        forward.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(forward);
    }
}
