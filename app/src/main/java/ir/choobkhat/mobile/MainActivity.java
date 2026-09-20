package ir.choobkhat.mobile;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
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
import java.util.ArrayList;
import java.util.Locale;
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
    private static final int PICK_FILE=101, SPEECH=102, MIC_PERMISSION=103;
    private static final int MAX_FILE=2*1024*1024;
    private String pendingName="";
    private String pendingText="";
    private String pendingImage="";
    private MediaRecorder recorder;
    private File recordedAudio;
    private TextToSpeech tts;
    private boolean voiceMode=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(dark);
        prefs = getSharedPreferences("choobkhat_settings", MODE_PRIVATE);
        tts=new TextToSpeech(this, result -> {
            if(result==TextToSpeech.SUCCESS){tts.setLanguage(new Locale("fa", "IR"));}
        });
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
        Button mic = button("🎙 مکالمه صوتی", () -> startVoice()); extras.addView(mic);
        Button record = button("● ضبط صدا", () -> toggleRecording()); extras.addView(record);
        Button attach = button("＋ عکس/فایل/صدا", () -> chooseFile()); extras.addView(attach);
        if(!pendingName.isEmpty()) add(content, text("پیوست آماده: "+pendingName+" (با ارسال پیام فرستاده می‌شود)",12,dark));
        Button clear = button("گفتگوی جدید", () -> { if(busy) return; while(history.length()>0) history.remove(0); showFeature(); }); extras.addView(clear);
    }
    private void addBubble(String message, boolean user) {
        TextView b = text(message, 16, user ? Color.WHITE : dark); b.setBackground(bg(user ? turquoise : Color.WHITE, 20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(user ? 45 : 0, 7, user ? 0 : 45, 7); chatMessages.addView(b,p);
        if(chatScroll!=null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }
    private void sendMessage() {
        if (busy) return;
        String message = chatInput.getText().toString().trim();
        if(message.isEmpty() && pendingImage.isEmpty() && pendingText.isEmpty()) return;
        if(message.isEmpty()) message="لطفاً پیوست را بررسی کن.";
        final String visibleMessage=message;
        final String attachmentText=pendingText, attachmentImage=pendingImage, attachmentName=pendingName;
        String key = getKey(), model = prefs.getString("model", "gpt-5.4-mini").trim();
        if (key.isEmpty() || model.isEmpty()) { toast("ابتدا کلید API و نام مدل را در تنظیمات وارد کنید."); return; }
        if (!"api".equals(prefs.getString("mode", "api"))) { toast("اتصال سرور داخلی هنوز پیاده‌سازی نشده؛ حالت API را انتخاب کنید."); return; }
        try {
            Object contentValue=message;
            if(!attachmentImage.isEmpty()) {
                JSONArray parts=new JSONArray();
                parts.put(new JSONObject().put("type","text").put("text",message));
                parts.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",attachmentImage)));
                contentValue=parts;
            } else if(!attachmentText.isEmpty()) contentValue=message+"\n\n--- پیوست: "+attachmentName+" ---\n"+attachmentText;
            history.put(new JSONObject().put("role","user").put("content",contentValue));
        } catch(Exception e) {toast("پیوست معتبر نیست.");return;}
        addBubble(visibleMessage+(attachmentName.isEmpty()?"":" 📎 "+attachmentName),true);
        pendingName="";pendingImage="";pendingText="";
        chatInput.setText(""); busy=true;
        executor.execute(() -> {
            try {
                JSONObject body=new JSONObject(); body.put("model",model); body.put("messages",new JSONArray(history.toString()));
                JSONObject response=request("POST", apiUrl()+"/chat/completions",key,body);
                JSONArray choices=response.getJSONArray("choices"); String answer=choices.getJSONObject(0).getJSONObject("message").optString("content", "");
                if(answer.isEmpty()) throw new IOException("پاسخ متنی خالی است؛ نام مدل را بررسی کنید.");
                main.post(() -> { try { history.put(new JSONObject().put("role","assistant").put("content",answer)); } catch(Exception ignored){} if(selected==6) addBubble(answer,false); status.setTextColor(Color.GREEN); busy=false;
                    if(voiceMode) { speak(answer); } });
            } catch(Exception e) { main.post(() -> { if(selected==6) addBubble("خطای ارتباط: "+e.getMessage(),false); status.setTextColor(Color.RED); busy=false; voiceMode=false; }); }
        });
    }
    private void chooseFile() {
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","audio/*","text/plain","text/csv","application/json"});
        startActivityForResult(pick,PICK_FILE);
    }
    private void startVoice() {
        voiceMode=true;
        listen();
    }
    private void listen() {
        if(busy) {toast("منتظر پاسخ قبلی باشید.");return;}
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"fa-IR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"صحبت کنید؛ برای پایان، بازگشت را بزنید.");
        try{startActivityForResult(i,SPEECH);}catch(Exception e){voiceMode=false;toast("تشخیص گفتار روی این دستگاه موجود نیست؛ از ضبط صدا استفاده کنید.");}
    }
    private void speak(String answer){
        if(tts==null){voiceMode=false;return;}
        String spoken=answer.length()>3500?answer.substring(0,3500):answer;
        tts.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,"choobkhat_reply");
        // User taps the voice button again to continue; do not auto-open microphone.
        toast("پاسخ صوتی پخش می‌شود. برای ادامه دوباره مکالمه صوتی را بزنید.");
        voiceMode=false;
    }
    private void toggleRecording(){
        if(recorder!=null){
            try{recorder.stop();recorder.release();recorder=null;toast("ضبط تمام شد؛ در حال تبدیل صدا به متن...");transcribeAudio(recordedAudio,"صدای ضبط‌شده","audio/mp4");}
            catch(Exception e){recorder.release();recorder=null;toast("ضبط صدا ناموفق بود.");}
            return;
        }
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);return;
        }
        try{
            recordedAudio=File.createTempFile("choobkhat-", ".m4a",getCacheDir());
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(recordedAudio.getAbsolutePath());
            recorder.prepare();recorder.start();toast("در حال ضبط... برای پایان دوباره «ضبط صدا» را بزنید.");
        }catch(Exception e){if(recorder!=null){recorder.release();recorder=null;}toast("میکروفون در دسترس نیست.");}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==MIC_PERMISSION && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)toggleRecording();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null){if(requestCode==SPEECH)voiceMode=false;return;}
        if(requestCode==SPEECH){
            ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(words!=null&&!words.isEmpty()&&selected==6){chatInput.setText(words.get(0));sendMessage();}
            else voiceMode=false;
        }else if(requestCode==PICK_FILE && data.getData()!=null){processPicked(data.getData());}
    }
    private void processPicked(Uri uri){
        String mime=getContentResolver().getType(uri);if(mime==null)mime="";
        String name="پیوست";
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        final String displayName=name;
        if(mime.startsWith("audio/")){
            try{
                File file=File.createTempFile("attached-", ".audio",getCacheDir());
                try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(file)){
                    byte[] buffer=new byte[8192];int n;int total=0;
                    while((n=in.read(buffer))!=-1){total+=n;if(total>10*1024*1024)throw new IOException("فایل صوتی بیش از ۱۰ مگابایت است.");out.write(buffer,0,n);}
                }
                transcribeAudio(file,displayName,mime);
            }catch(Exception e){toast("خطا در خواندن صدا: "+e.getMessage());}return;
        }
        try {
            if(mime.startsWith("image/")){
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
                int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
                Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
                if(bitmap==null)throw new IOException("تصویر خوانده نشد.");
                ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,78,out);bitmap.recycle();
                if(out.size()>MAX_FILE)throw new IOException("تصویر بزرگ است؛ تصویر کوچک‌تری انتخاب کنید.");
                pendingImage="data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
                pendingText="";pendingName=displayName;
            }else if(mime.startsWith("text/")||mime.equals("application/json")||displayName.endsWith(".csv")||displayName.endsWith(".txt")){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    byte[] buffer=new byte[4096];int n;
                    while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>MAX_FILE)throw new IOException("فایل متنی بیش از ۲ مگابایت است.");}
                }
                pendingText=out.toString("UTF-8");if(pendingText.length()>40000)pendingText=pendingText.substring(0,40000)+"\n[ادامه فایل به دلیل محدودیت حجم حذف شد]";
                pendingImage="";pendingName=displayName;
            }else{toast("فعلاً عکس، صدا و فایل متنی/CSV/JSON پشتیبانی می‌شود؛ PDF و Word هنوز فعال نیستند.");return;}
            if(selected==6) {
                String draft=chatInput==null?"":chatInput.getText().toString();
                showFeature();chatInput.setText(draft);
            }
            toast("پیوست آماده ارسال است.");
        }catch(Exception e){toast("خواندن فایل ناموفق بود: "+e.getMessage());}
    }
    private void transcribeAudio(File audio,String filename,String mime){
        if(busy){toast("ابتدا منتظر پاسخ فعلی باشید.");return;}
        String key=getKey();if(key.isEmpty()){toast("کلید AvalAI را در تنظیمات وارد کنید.");return;}
        busy=true;
        executor.execute(()->{
            try{
                URL url=new URL(apiUrl()+"/audio/transcriptions");
                if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("HTTPS الزامی است.");
                String boundary="----choobkhat"+System.currentTimeMillis();
                HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                try{
                    conn.setRequestMethod("POST");conn.setConnectTimeout(15000);conn.setReadTimeout(90000);conn.setDoOutput(true);
                    conn.setRequestProperty("Authorization","Bearer "+key);
                    conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
                    try(OutputStream out=conn.getOutputStream();FileInputStream in=new FileInputStream(audio)){
                        String audioType=mime.startsWith("audio/")?mime:"audio/mp4";
                        String extension=audioType.contains("mpeg")?"mp3":audioType.contains("wav")?"wav":audioType.contains("ogg")?"ogg":"m4a";
                        String header="--"+boundary+"\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"+
                            prefs.getString("transcription_model","groq.whisper-large-v3")+"\r\n"+
                            "--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio."+extension+"\"\r\nContent-Type: "+audioType+"\r\n\r\n";
                        out.write(header.getBytes(StandardCharsets.UTF_8));byte[] buf=new byte[8192];int n;
                        while((n=in.read(buf))!=-1)out.write(buf,0,n);
                        out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                    }
                    int code=conn.getResponseCode();InputStream stream=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=stream){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>MAX_FILE)throw new IOException("پاسخ بزرگ است.");}}
                    String body=bytes.toString("UTF-8");if(code<200||code>=300)throw new IOException("HTTP "+code+": "+body.substring(0,Math.min(150,body.length())));
                    String transcript=new JSONObject(body).optString("text","");if(transcript.isEmpty())throw new IOException("متن صدا دریافت نشد.");
                    main.post(()->{busy=false;if(selected==6){chatInput.setText(transcript);toast("متن صدا آماده است؛ برای ارسال، دکمه ارسال را بزنید.");}});
                }finally{conn.disconnect();}
            }catch(Exception e){main.post(()->{busy=false;toast("تبدیل صدا ناموفق بود: "+e.getMessage());});}
            finally{audio.delete();}
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
    @Override protected void onDestroy(){
        if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}
        if(tts!=null){tts.stop();tts.shutdown();}
        executor.shutdownNow();super.onDestroy();
    }
}
