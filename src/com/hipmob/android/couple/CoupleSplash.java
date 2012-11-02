package com.hipmob.android.couple;

import com.hipmob.android.HipmobPushService;
import com.hipmob.android.HipmobCore;
import com.hipmob.android.HipmobPendingMessageListener;
import com.hipmob.android.HipmobRemoteConnection;

import java.util.UUID;

import org.json.JSONObject;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gcm.GCMRegistrar;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

public class CoupleSplash extends Activity 
{
	private static final int SPLASH_DONE = 1;

	private static final long SPLASH_DELAY = 3000;
	
	private MixpanelAPI mMixpanel;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// Be sure to call the super class.
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		
		mMixpanel = MixpanelAPI.getInstance(this, App.MIXPANEL_ID);

		String guid = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString(getString(R.string.pref_guid), "");
		if(!"".equals(guid)){
			try{
				JSONObject superProperties = new JSONObject();
				superProperties.put("guid", guid);
				mMixpanel.registerSuperProperties(superProperties);
			}catch(Exception e1){}
		}
		
		setContentView(R.layout.splash);
		msgHandler.sendEmptyMessageDelayed(SPLASH_DONE, SPLASH_DELAY);
		
		/*
		GCMRegistrar.checkDevice(this);
		final String regId = GCMRegistrar.getRegistrationId(this);
		if (regId.equals("")) {
		  GCMRegistrar.register(this, App.SENDER_ID);
		} else {
		  ((App)getApplication()).setGCMID(regId);
		}
		*/
		HipmobPushService.setup(this);
	}

	private Handler msgHandler = new Handler() {
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what) {
			case SPLASH_DONE:
				splashDone();
				break;
			}
		}
	};

	void splashDone()
	{
		// go to the login screen
		Intent i = new Intent(this, CoupleLogin.class);
		i.putExtra(Intent.ACTION_MAIN, Boolean.TRUE);
		startActivity(i);
		finish();
	}
	

	@Override
	protected void onDestroy()
	{
		mMixpanel.flush();
		super.onDestroy();
		//App.nullViewDrawablesRecursive(findViewById(R.id.root));
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
	}
	
	@Override
	protected void onStop()
	{
		super.onStop();
	}
}
