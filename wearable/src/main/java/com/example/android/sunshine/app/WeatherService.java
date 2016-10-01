/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */

public class WeatherService extends CanvasWatchFaceService{

    private static final String TAG = WeatherService.class.getSimpleName();

    @Override
    public Engine onCreateEngine()
    {
        return new WatchFaceEngine();
    }

    private class WatchFaceEngine extends Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener
    {

        //private static final String COUNT_KEY = "com.example.key.count";
        //private int count = 0;

        //Member variables
        private Typeface WATCH_TEXT_TYPEFACE = Typeface.create( Typeface.SERIF, Typeface.NORMAL );

        private boolean mWeatherDataUpdated=false;

        //Keeping track of current device time

        private static final int MSG_UPDATE_TIME_ID = 42;


        private static final String WEARABLE_DATA_PATH = "/wearable_data";

        //update every second
        private long mUpdateRateMs = 1000;

        private long DEFAULT_UPDATE_RATE_MS=500;

        private Time mDisplayTime;

        private Paint mBackgroundColorPaint;
        private Paint mTextColorPaint;

        private boolean mHasTimeZoneReceiverBeenRegistered = false;
        private boolean mIsInMuteMode;
        private boolean mIsLowBitAmbient;

        //Watch face drawn without cuts at the corners
        private float mXOffset;
        private float mYOffset;

        private Bitmap mWeatherIconBitmap;
        private Bitmap mGrayWeatherIconBitmap;
        private String mHighTemp;
        private String mLowTemp;

        private int mBackgroundColor = Color.parseColor( "black" );
        private int mTextColor = Color.parseColor( "red" );


        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WeatherService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();


        //this receiver clears the saved time zone and displays the changed timezone
        final BroadcastReceiver mTimeZoneBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mDisplayTime.clear( intent.getStringExtra( "time-zone" ) );
                mDisplayTime.setToNow();
            }
        };

        //handler to take care of updating watch face every second
        private final Handler mTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch( msg.what ) {
                    case MSG_UPDATE_TIME_ID: {
                        invalidate();
                        if( isVisible() && !isInAmbientMode() ) {
                            long currentTimeMillis = System.currentTimeMillis();
                            long delay = mUpdateRateMs - ( currentTimeMillis % mUpdateRateMs );
                            mTimeHandler.sendEmptyMessageDelayed( MSG_UPDATE_TIME_ID, delay );
                        }
                        break;
                    }
                }
            }
        };

        //System interacts with user when watch face is active
        //Not to display default time

        @Override
        public void onCreate(SurfaceHolder holder) {

            super.onCreate(holder);

            setWatchFaceStyle( new WatchFaceStyle.Builder( WeatherService.this )
                    .setBackgroundVisibility( WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE )
                    .setCardPeekMode( WatchFaceStyle.PEEK_MODE_VARIABLE )
                    .setShowSystemUiTime( false )
                    .build()
            );

            mDisplayTime = new Time(); //mDisplayTime is new Customized Time Object

            initBackground();

            initDisplayText();

            initWeatherDetails(0,0,"clear");
        }

        private void initBackground()
        {

            mBackgroundColorPaint = new Paint();
            mBackgroundColorPaint.setColor( mBackgroundColor );

        }

        private void initDisplayText()
        {

            mTextColorPaint = new Paint();
            mTextColorPaint.setColor( mTextColor );
            mTextColorPaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mTextColorPaint.setAntiAlias( true );   //Reduce the clarity of text inorder to perform well
            mTextColorPaint.setTextSize( getResources().getDimension( R.dimen.text_size ) );

        }


        @Override
        public void onVisibilityChanged( boolean visible ) {
            super.onVisibilityChanged(visible);

            if( visible ) {

                mGoogleApiClient.connect();
                // checks for Broadcast Receiver is registered or not
                if( !mHasTimeZoneReceiverBeenRegistered ) {

                    IntentFilter filter = new IntentFilter( Intent.ACTION_TIMEZONE_CHANGED );
                    WeatherService.this.registerReceiver( mTimeZoneBroadcastReceiver, filter );

                    mHasTimeZoneReceiverBeenRegistered = true;
                }

                mDisplayTime.clear( TimeZone.getDefault().getID() );
                mDisplayTime.setToNow();
            }
            else
            {
                if( mHasTimeZoneReceiverBeenRegistered )
                {
                    WeatherService.this.unregisterReceiver( mTimeZoneBroadcastReceiver );
                    mHasTimeZoneReceiverBeenRegistered = false;
                }

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected())
                {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            updateTimer();
        }


        private void updateTimer()
        {
            mTimeHandler.removeMessages( MSG_UPDATE_TIME_ID );
            if( isVisible() && !isInAmbientMode() ) {
                mTimeHandler.sendEmptyMessage( MSG_UPDATE_TIME_ID );
            }
        }

        //used for round and squared
        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            mYOffset = getResources().getDimension( R.dimen.y_offset );

            if( insets.isRound() ) {
                mXOffset = getResources().getDimension( R.dimen.x_offset_round );
            } else {
                mXOffset = getResources().getDimension( R.dimen.x_offset_square );
            }
        }


        //checks hardware properties like burn-in mode and low-bit ambient mode
        @Override
        public void onPropertiesChanged( Bundle properties ) {
            super.onPropertiesChanged( properties );

            if( properties.getBoolean( PROPERTY_BURN_IN_PROTECTION, false ) ) {
                mIsLowBitAmbient = properties.getBoolean( PROPERTY_LOW_BIT_AMBIENT, false );
            }
        }

        //differs background colour in ambient and interactive
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if( inAmbientMode ) {
                mTextColorPaint.setColor( Color.parseColor( "white" ) );
            } else {
                mTextColorPaint.setColor( Color.parseColor( "red" ) );
            }

            if( mIsLowBitAmbient ) {
                mTextColorPaint.setAntiAlias( !inAmbientMode );
            }

            invalidate();
            updateTimer();
        }


        //when there are Interruptions like Settings menu.update the Watch face accordingly
        //update timer every minute rather than every second
        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean isDeviceMuted = ( interruptionFilter == android.support.wearable.watchface.WatchFaceService.INTERRUPTION_FILTER_NONE );
            if( isDeviceMuted ) {
                mUpdateRateMs = TimeUnit.MINUTES.toMillis( 1 );
            } else {
                mUpdateRateMs = DEFAULT_UPDATE_RATE_MS;
            }

            if( mIsInMuteMode != isDeviceMuted ) {
                mIsInMuteMode = isDeviceMuted;
                int alpha = ( isDeviceMuted ) ? 100 : 255;
                mTextColorPaint.setAlpha( alpha );
                invalidate();
                updateTimer();
            }
        }


        //When watch is in ambient mode.it updates time every minute using this method

        @Override
        public void onTimeTick()
        {

            super.onTimeTick();
            invalidate();

        }

        //Drawing out watch face manually
        @Override
        public void onDraw(Canvas canvas, Rect bounds)
        {

            super.onDraw(canvas, bounds);

            mDisplayTime.setToNow();

            drawBackground( canvas, bounds );

            drawTimeText( canvas );

            drawTemperatureText( canvas );

        }


        //Applying solid colour to background of wear device
        private void drawBackground( Canvas canvas, Rect bounds ) {
            canvas.drawRect( 0, 0, bounds.width(), bounds.height(), mBackgroundColorPaint );
        }


        //Creating Time Text with help of canvas methdod

        private void drawTimeText( Canvas canvas ) {
            String timeText = getHourString() + ":" + String.format( "%02d", mDisplayTime.minute );
            if( isInAmbientMode() || mIsInMuteMode ) {
                timeText += ( mDisplayTime.hour < 12 ) ? "AM" : "PM";
            }
            else
            {
                timeText += String.format( ":%02d", mDisplayTime.second);
            }
            canvas.drawText( timeText, mXOffset, mYOffset, mTextColorPaint );
        }

        private void drawTemperatureText(Canvas canvas)
        {



        }

        private String getHourString() {
            if( mDisplayTime.hour % 12 == 0 )
                return "12";
            else if( mDisplayTime.hour <= 12 )
                return String.valueOf( mDisplayTime.hour );
            else
                return String.valueOf( mDisplayTime.hour - 12 );
        }


        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged");
            for (DataEvent dataEvent : dataEvents)
            {

                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        WEARABLE_DATA_PATH)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dMap = dataMapItem.getDataMap();
                int high = (int) Math.round(dMap.getDouble("high"));
                int low = (int) Math.round(dMap.getDouble("low"));
                Long id = dMap.getLong("id");
                String icon = Utility.getArtUrlForWeatherCondition(id);
                initWeatherDetails(high, low, icon);
                invalidate();

            }
            mWeatherDataUpdated = true;
        }

        private void initWeatherDetails(int high, int low, String icon  ){
            if(icon == null){
                icon = "clear";
            }
            int resID = getResources().getIdentifier("ic_" + icon , "drawable", getPackageName());
            int resIDBW = getResources().getIdentifier("ic_" + icon + "_bw" , "drawable", getPackageName());

            mWeatherIconBitmap = BitmapFactory.decodeResource(getResources(), resID);
            mGrayWeatherIconBitmap= BitmapFactory.decodeResource(getResources(), resIDBW);
            mHighTemp = String.format("%3s",String.valueOf(high)) + "° C";
            mLowTemp = String.format("%3s",String.valueOf(low)) + "° C";

        }

        @Override
        public void onDestroy() {
            mTimeHandler.removeMessages(MSG_UPDATE_TIME_ID);
            super.onDestroy();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, WatchFaceEngine.this);

        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }

        class SendToDataLayerThread extends Thread {
            String path;
            DataMap dataMap;

            // Constructor for sending data objects to the data layer
            SendToDataLayerThread(String p, DataMap data) {
                path = p;
                dataMap = data;
            }

            public void run() {
                // Construct a DataRequest and send over the data layer
                PutDataMapRequest putDMR = PutDataMapRequest.create(path);
                putDMR.getDataMap().putAll(dataMap);
                PutDataRequest request = putDMR.asPutDataRequest();
                DataApi.DataItemResult result = Wearable.DataApi.putDataItem(mGoogleApiClient, request).await();
                if (result.getStatus().isSuccess()) {
                    Log.d(TAG, "DataMap: " + dataMap + " sent successfully to data layer ");
                } else {
                    // Log an error
                    Log.d(TAG, "ERROR: failed to send DataMap to data layer");
                }
            }
        }
    }


}