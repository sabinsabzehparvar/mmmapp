package ir.choobkhat.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final String[] features = {"گزارش کالا", "آدرس انبار و محل نگهداری", "وظایف زمان‌بندی‌شده و یادآوری", "ارتباط کاربران", "بررسی پیامک‌های بانکی", "پشتیبان‌گیری و بازیابی", "سایر درخواست‌ها • هوش مصنوعی"};
    private final int turquoise = Color.rgb(0, 153, 153), dark = Color.rgb(24, 35, 42);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final JSONArray history = new JSONArray();
    private LinearLayout root, content, topBar;
    private TextView status;
    private ScrollView chatScroll;
    private LinearLayout chatMessages;
    private EditText chatInput;
    private int selected = 6;
    private boolean busy = false;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(dark);
        prefs = getSharedPreferences("choobkhat_settings", MODE_PRIVATE);
        render();
    }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(size); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(16, 13, 16, 13); return t;
    }
    private void add(LinearLayout target, View v) { target.addView(v, new LinearLayout.LayoutParams(-1, -2)); }
    private Button button(String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackground(bg(turquoise, 18)); b.setOnClickListener(v -> action.run()); return b;
    }
    private EditText input(String hint) {
        EditText e = new EditText(this); e.setTextSize(15); e.setHint(hint); e.setSingleLine(true); e.setPadding(18, 12, 18, 12); return e;
    }
    private void render() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(247, 250, 250)); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); setContentView(root);
        topBar = new LinearLayout(this); topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setBackgroundColor(dark); topBar.setPadding(6, 7, 6, 7); add(root, topBar);
        TextView menu = text("☰", 27, Color.WHITE); topBar.addView(menu); menu.setOnClickListener(v -> showMenu());
        TextView name = text("چوب‌خط", 19, Color.WHITE); name.setTypeface(null, Typeface.BOLD); topBar.addView(name, new LinearLayout.LayoutParams(0,-2,1));
        status = text("●", 23, Color.RED); topBar.addView(status); status.setOnClickListener(v -> testConnection());
        TextView inbox = text("✉", 23, Color.LTGRAY); topBar.addView(inbox); inbox.setOnClickListener(v -> toast("صندوق پیام در نسخه بعدی فعال می‌شود."));
        TextView settings = text("⚙", 25, Color.WHITE); topBar.addView(settings); settings.setOnClickListener(v -> showSettings());
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(18, 16, 18, 16); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        showFeature();
        if (!getKey().isEmpty()) testConnection();
    }
    private void showMenu() {
        new AlertDialog.Builder(this).setTitle("قابلیت‌های چوب‌خط").setItems(features, (d, which) -> { selected = which; showFeature(); }).show();
    }
    private void showFeature() {
        content.removeAllViews();
        TextView title = text(features[selected], 21, dark); title.setTypeface(null, Typeface.BOLD); add(content, title);
        if (selected == 6) { showChat(); return; }
        if (selected == 0 || selected == 1) {
            EditText item = input(selected == 0 ? "نام کالا یا کد کالا" : "نام کالا یا کد کالا برای جستجوی انبار"); add(content, item);
            add(content, button("جستجو", () -> toast("بانک اطلاعاتی هنوز متصل نشده است.")));
            add(content, text("نتیجه پس از اتصال بانک اطلاعاتی اینجا نمایش داده می‌شود.", 14, Color.GRAY));
        } else if (selected == 2) {
            add(content, input("عنوان وظیفه")); add(content, input("تاریخ و زمان یادآوری")); add(content, button("ثبت یادآوری (به‌زودی)", () -> toast("زمان‌بندی هنوز فعال نشده است.")));
        } else if (selected == 3) {
            add(content, input("نام کاربر")); add(content, button("شروع گفتگو (به‌زودی)", () -> toast("سرور پیام‌رسان هنوز متصل نشده است.")));
        } else if (selected == 4) {
            add(content, text("دریافت و پردازش پیامک‌های بانکی در این نسخه فعال نیست؛ هیچ مجوز SMS درخواست نمی‌شود.", 15, Color.GRAY));
        } else {
            add(content, button("تهیه نسخه پشتیبان (به‌زودی)", () -> toast("پشتیبان‌گیری هنوز فعال نشده است.")));
            add(content, button("بازیابی (به‌زودی)", () -> toast("بازیابی هنوز فعال نشده است.")));
        }
    }
    private void showChat() {
        chatScroll = new ScrollView(this); chatMessages = new LinearLayout(this); chatMessages.setOrientation(LinearLayout.VERTICAL); chatScroll.addView(chatMessages);
        content.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (history.length() == 0) addBubble("سلام! برای گفتگو، کلید AvalAI و نام مدل را در ⚙ تنظیمات ارتباط وارد کنید.", false);
        else for (int i=0;i<history.length();i++) { JSONObject m=history.optJSONObject(i); if(m!=null) addBubble(m.optString("content"),"user".equals(m.optString("role"))); }
        LinearLayout sendRow = new LinearLayout(this); sendRow.setGravity(Gravity.CENTER_VERTICAL); add(content, sendRow);
        chatInput = input("پیام خود را بنویسید…"); chatInput.setSingleLine(false); chatInput.setMinLines(1); chatInput.setMaxLines(4); sendRow.addView(chatInput, new LinearLayout.LayoutParams(0,-2,1));
        Button send = button("ارسال", () -> sendMessage()); sendRow.addView(send);
        LinearLayout extras = new LinearLayout(this); add(content, extras);
        Button mic = button("🎙 صوت (به‌زودی)", () -> toast("قابلیت صوتی هنوز فعال نیست.")); extras.addView(mic);
        Button clear = button("گفتگوی جدید", () -> { if(busy) return; while(history.length()>0) history.remove(0); showFeature(); }); extras.addView(clear);
    }
    private void addBubble(String message, boolean user) {
        TextView b = text(message, 16, user ? Color.WHITE : dark); b.setBackground(bg(user ? turquoise : Color.WHITE, 20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(user ? 45 : 0, 7, user ? 0 : 45, 7); chatMessages.addView(b,p);
        if(chatScroll!=null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }
    private void sendMessage() {
        if (busy) return;
        String message = chatInput.getText().toString().trim(); if(message.isEmpty()) return;
        String key = getKey(), model = prefs.getString("model", "gpt-5.4-mini").trim();
        if (key.isEmpty() || model.isEmpty()) { toast("ابتدا کلید API و نام مدل را در تنظیمات وارد کنید."); return; }
        if (!"api".equals(prefs.getString("mode", "api"))) { toast("اتصال سرور داخلی هنوز پیاده‌سازی نشده؛ حالت API را انتخاب کنید."); return; }
        try { history.put(new JSONObject().put("role","user").put("content",message)); } catch(Exception ignored) {}
        addBubble(message,true); chatInput.setText(""); busy=true;
        executor.execute(() -> {
            try {
                JSONObject body=new JSONObject(); body.put("model",model); body.put("messages",new JSONArray(history.toString()));
                JSONObject response=request("POST", apiUrl()+"/chat/completions",key,body);
                JSONArray choices=response.getJSONArray("choices"); String answer=choices.getJSONObject(0).getJSONObject("message").optString("content", "");
                if(answer.isEmpty()) throw new IOException("پاسخ متنی خالی است؛ نام مدل را بررسی کنید.");
                main.post(() -> { try { history.put(new JSONObject().put("role","assistant").put("content",answer)); } catch(Exception ignored){} if(selected==6) addBubble(answer,false); status.setTextColor(Color.GREEN); busy=false; });
            } catch(Exception e) { main.post(() -> { if(selected==6) addBubble("خطای ارتباط: "+e.getMessage(),false); status.setTextColor(Color.RED); busy=false; }); }
        });
    }
    private String apiUrl() { return prefs.getString("api_url","https://api.avalai.ir/v1").replaceAll("/+$", ""); }
    private JSONObject request(String method, String address, String key, JSONObject body) throws Exception {
        URL url = new URL(address); if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("برای امنیت، فقط HTTPS مجاز است.");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection(); conn.setConnectTimeout(15000); conn.setReadTimeout(60000); conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization","Bearer "+key); conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try {
            if(body!=null){conn.setDoOutput(true); try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=conn.getResponseCode(); InputStream stream=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
            String result=""; if(stream!=null)try(InputStream in=stream; ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>1048576)throw new IOException("پاسخ بیش از حد بزرگ است.");}result=bytes.toString("UTF-8");}
            if(code<200||code>=300){String detail=result;try{ detail=new JSONObject(result).getJSONObject("error").optString("message",result); }catch(Exception ignored){} throw new IOException("HTTP "+code+": "+detail.substring(0,Math.min(180,detail.length())));}
            return new JSONObject(result);
        } finally {conn.disconnect();}
    }
    private void testConnection() {
        String key=getKey(); if(key.isEmpty()||!"api".equals(prefs.getString("mode","api"))){status.setTextColor(Color.RED);toast("برای آزمایش اتصال، حالت API و کلید را تنظیم کنید.");return;}
        status.setTextColor(Color.YELLOW);
        executor.execute(() -> { try { request("GET",apiUrl()+"/models",key,null);main.post(() -> {status.setTextColor(Color.GREEN);toast("اتصال API برقرار است.");});}
            catch(Exception e){main.post(() -> {status.setTextColor(Color.RED);toast("اتصال برقرار نشد: "+e.getMessage());});} });
    }
    private void showSettings() {
        String[] options={"پروفایل (فقط نمایش)","تنظیمات عمومی", "تنظیمات ارتباط"};
        new AlertDialog.Builder(this).setTitle("تنظیمات").setItems(options,(d,i)-> {if(i==0)new AlertDialog.Builder(this).setTitle("پروفایل").setMessage("پس از اتصال سامانه، اطلاعات کاربر اینجا نمایش داده می‌شود.").setPositiveButton("بستن",null).show(); else if(i==1)generalSettings(); else connectionSettings();}).show();
    }
    private void generalSettings() { new AlertDialog.Builder(this).setTitle("تنظیمات عمومی").setMessage("رنگ و تم در نسخه‌های بعدی قابل تنظیم خواهند بود.").setPositiveButton("بستن",null).show(); }
    private void connectionSettings() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,10,24,5);
        Spinner mode=new Spinner(this);String[] modes={"API مستقیم AvalAI", "سرور داخلی چوب‌خط (آینده)"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes);mode.setAdapter(adapter);mode.setSelection("api".equals(prefs.getString("mode","api"))?0:1);add(form,mode);
        EditText base=input("آدرس پایه API");base.setText(prefs.getString("api_url","https://api.avalai.ir/v1"));add(form,base);
        EditText model=input("نام مدل، مثلاً gpt-5.4-mini");model.setText(prefs.getString("model","gpt-5.4-mini"));add(form,model);
        EditText key=input("کلید API (در دستگاه رمزگذاری می‌شود)");key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);add(form,key);
        add(form,text("کلید فعلی: "+(getKey().isEmpty()?"ثبت نشده":"ثبت شده؛ برای حفظ آن کادر را خالی بگذارید"),12,Color.GRAY));
        new AlertDialog.Builder(this).setTitle("تنظیمات ارتباط").setView(form).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",(d,w)-> {
            String url=base.getText().toString().trim().replaceAll("/+$", "");
            if(mode.getSelectedItemPosition()==0&&!url.startsWith("https://")){toast("آدرس API باید با https:// شروع شود.");return;}
            prefs.edit().putString("mode",mode.getSelectedItemPosition()==0?"api":"local").putString("api_url",url).putString("model",model.getText().toString().trim()).apply();
            String newKey=key.getText().toString().trim(); if(!newKey.isEmpty()) {try{storeKey(newKey);}catch(Exception e){toast("خطا در ذخیره امن کلید: "+e.getMessage());return;}}
            status.setTextColor(Color.RED);toast("تنظیمات ذخیره شد."); if(mode.getSelectedItemPosition()==0&&!getKey().isEmpty())testConnection();
        }).show();
    }
    private SecretKey secret() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="choobkhat_api_key";
        if(store.containsAlias(alias)) return ((KeyStore.SecretKeyEntry)store.getEntry(alias,null)).getSecretKey();
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private void storeKey(String value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secret());
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("key_cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("key_iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).apply();
    }
    private String getKey() {
        try {String c=prefs.getString("key_cipher","");String iv=prefs.getString("key_iv","");if(c.isEmpty()||iv.isEmpty())return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(c,Base64.NO_WRAP)),StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
