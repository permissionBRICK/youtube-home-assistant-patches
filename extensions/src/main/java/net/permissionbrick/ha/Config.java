// SPDX-License-Identifier: GPL-3.0-only
package net.permissionbrick.ha;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Config {
    private static final String ALIAS = "ha_send_to_tv_webhook_v1";
    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "ha_send_to_tv.bin"));
    }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    static String read(Context context) throws Exception {
        if (!file(context).getBaseFile().exists()) return "";
        byte[] data = file(context).readFully();
        if (data.length < 29) throw new IllegalStateException("Invalid saved settings");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOfRange(data, 0, 12)));
        return new String(cipher.doFinal(Arrays.copyOfRange(data, 12, data.length)), StandardCharsets.UTF_8);
    }
    static void save(Context context, String endpoint) throws Exception {
        endpoint = Webhook.validate(endpoint);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        AtomicFile target = file(context);
        FileOutputStream out = target.startWrite();
        try {
            out.write(cipher.getIV());
            out.write(cipher.doFinal(endpoint.getBytes(StandardCharsets.UTF_8)));
            target.finishWrite(out);
        } catch (Exception e) { target.failWrite(out); throw e; }
    }
    static void clear(Context context) { file(context).delete(); }
}
