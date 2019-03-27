package com.mgosu.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import static android.nfc.NfcAdapter.EXTRA_DATA;

public class MyService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mediaProjectionManager;
    private static final int REQUESTRESULT = 0x100;
    private int mWidth;
    private int mHeight;
    private int mScreenDensity;
    private ImageReader mImageReader;
    private WindowManager windowManager;

    private Notification notification;
    private NotificationManager notificationManager;
    private RemoteViews remoteViews;
    private NotificationCompat.Builder builder;
    private static final int ID_NOTIFICATION_VIDEO = 11123;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter filter;
    private Image image =null;

    public static Intent newIntent(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, MyService.class);
        intent.putExtra("result-code", resultCode);
        intent.putExtra(EXTRA_DATA, data);
        intent.putExtra("CMD", "START_RECORD");
        return intent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNoti();
        startForeground(ID_NOTIFICATION_VIDEO,notification);
        eventClick();
        if (intent != null) {
            String extra = intent.getStringExtra("CMD");
            if (extra != null) {
                if (extra.equalsIgnoreCase("START_RECORD")) {
                    createImage();
                    int resultCode = intent.getIntExtra("result-code", 0);
                    Intent data = intent.getParcelableExtra(EXTRA_DATA);
                    Log.e("22", "width:"+mWidth+"height:"+mHeight );
                     mImageReader = ImageReader.newInstance(mWidth,mHeight, PixelFormat.RGBA_8888, 2);
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode,data);
                    mVirtualDisplay = mediaProjection.createVirtualDisplay("project",mWidth,mHeight,
                            mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,mImageReader.getSurface(),null,null);
                }
            }else {
                if (mediaProjection == null) {
                    intent = new Intent(MyService.this, Project.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);

                                }
            }
        }
        return START_NOT_STICKY;

    }

    private void eventClick() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()){
                    case "demo":
                        image = mImageReader.acquireLatestImage();

                        if (image != null) {
                            int width = image.getWidth();
                            int height = image.getHeight();
                            final Image.Plane[] planes = image.getPlanes();
                            final ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();
                            int rowPadding = rowStride - pixelStride * width;
                            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                                    Bitmap.Config.ARGB_8888);
                            bitmap.copyPixelsFromBuffer(buffer);
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                            image.close();
//                        imageView.setImageBitmap(bitmap);
//                        ll.setBackgroundColor(0x0f0);

                            String root = Environment.getExternalStorageDirectory().toString();
                            File myDir = new File(root);
                            myDir.mkdirs();
                            String fname = "Image-" + ".jpg";
                            File file = new File(myDir, fname);
                            if (file.exists()) file.delete();
                            Log.i("LOAD", root + fname);
                            try {
                                FileOutputStream out = new FileOutputStream(file);
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                                out.flush();
                                out.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        break;
                }
            }
        };
        filter = new IntentFilter();
        filter.addAction("demo");
        registerReceiver(broadcastReceiver,filter);
    }

    private void createNoti() {

        String channelId = "channel-02";
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setChannelId(channelId)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_MAX);


        builder.build().flags |= Notification.FLAG_AUTO_CANCEL;
        notification = builder.build();

        remoteViews = new RemoteViews(getPackageName(), R.layout.custom_service);
        notification.contentView = remoteViews;

        String channelName = "Channel Name Video";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(channelId, channelName, importance);
            notificationManager.createNotificationChannel(mChannel);
            notificationManager.notify(ID_NOTIFICATION_VIDEO, notification);
        } else {
            notificationManager.notify(ID_NOTIFICATION_VIDEO, notification);
        }

        Intent capture = new Intent("demo");
        PendingIntent capturePendingIntent = PendingIntent.getBroadcast(this, 3214, capture, 0);
        builder.addAction(R.id.layout, "demo", capturePendingIntent);
        builder.setContentIntent(capturePendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.layout, capturePendingIntent);

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createImage() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        mWidth = display.getWidth();
        mHeight = display.getHeight();
        DisplayMetrics outMetric = new DisplayMetrics();
        display.getMetrics(outMetric);
        mScreenDensity = (int) outMetric.density;
    }
}
