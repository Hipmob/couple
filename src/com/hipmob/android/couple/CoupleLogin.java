package com.hipmob.android.couple;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.UUID;

import android.os.Handler;
import android.os.Message;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import android.app.Activity;
import android.app.Dialog;
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
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;

public class CoupleLogin extends Activity implements OnClickListener 
{	
	private Button login;
	private EditText emailInput, passwordInput;
	private TextView signup;

	private static final String TAG = "CoupleLogin";
	
	private MixpanelAPI mMixpanel;
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		// remove the title bar
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
		
		if(getIntent().hasExtra("count")){
			// it is from a push notification.
			JSONObject props = new JSONObject();
			try{
				props.put("count", getIntent().getIntExtra("count", 0));
			}catch(Exception e1){}
			mMixpanel.track("Notification Tapped", props);
		}
		
		// see if we already have a login
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		if(prefs.contains(getString(R.string.pref_guid))){
			//startActivity(new Intent(this, CouplePreferences.class));
			startActivity(new Intent(this, CoupleSettings.class));
			
			finish();
			return;
		}
		// load the content view
		setContentView(R.layout.login);
		
		signup = (TextView)findViewById(R.id.signup);
		signup.setPaintFlags(signup.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
		signup.setOnClickListener(this);
		login = (Button)findViewById(R.id.login);
		login.setOnClickListener(this);
		
		emailInput = (EditText)findViewById(R.id.email);
		passwordInput = (EditText)findViewById(R.id.password);
		emailInput.requestFocus(); 
	}

	@Override
	public void onClick(View arg0)
	{
		if(signup == arg0){
			// start up the signup screen
			startActivity(new Intent(this, CoupleSignup.class));
			
			// we're done with this screen
			finish();			
		}else if(login == arg0){
			// check that we have a valid email address
			String email = emailInput.getText().toString().trim();
			String password= passwordInput.getText().toString().trim();
			
			if("".equals(email) || !App.EMAIL_ADDRESS.matcher(email).matches()){
				Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_LONG).show();
				return;
			}
			
			if("".equals(password)){
				Toast.makeText(this, getString(R.string.error_invalid_password), Toast.LENGTH_LONG).show();
				return;
			}
			
			// got them both: login
			arg0.setEnabled(false);
			
			new Thread(new Login(email, password)).start();
			
			// start the progress bar
			showDialog(DIALOG_LOGGING_IN);
		}
	}
	
	class Login implements Runnable
	{
		private String email, password;
		Login(String email, String password)
		{
			this.email = email;
			this.password = password;
		}
		
		public void run()
		{
			Message m = Message.obtain();
			m.what = FAILURE;
			m.obj = CoupleLogin.this.getString(R.string.error_unknown);
			try{
				// lets run the actual request
				StringBuilder ua = new StringBuilder(1024);
				ua.append("Android/Couple 1.0; ");
				ua.append("OS Version: ").append(System.getProperty("os.version")).append("(");
				ua.append(android.os.Build.VERSION.INCREMENTAL).append(");");
				ua.append("OS API Level: ").append(android.os.Build.VERSION.SDK_INT);
				ua.append(";Device: ").append(android.os.Build.DEVICE);
				ua.append("; Model: ").append(android.os.Build.MODEL);
				ua.append(" (").append(android.os.Build.PRODUCT).append(")");
	
				StringBuilder sb = new StringBuilder(300);
				sb.append("email=").append(URLEncoder.encode(email));
				sb.append("&password=").append(URLEncoder.encode(password));
				
				String res = App.executePost(App.SERVER_URI+"login", sb.toString(), ua.toString());
				if(res != null){
					JSONObject detail = (JSONObject) new JSONTokener(res).nextValue();
					if(detail.has("error")){
						m.what = FAILURE;
						m.obj = detail.getString("error");
					}else if(detail.has("guid") && detail.has("name")){
						m.what = SUCCESS;
						m.obj = detail;
					}
				}
			}catch(JSONException jse){
				m.what = FAILURE;
				m.obj = CoupleLogin.this.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["+jse.getMessage()+"]", jse);
			}catch(IOException ioe){
				m.what = FAILURE;
				m.obj = CoupleLogin.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG, "Exception logging in ["+ioe.getMessage()+"]", ioe);
			}
			msgHandler.sendMessageDelayed(m, 10);
		}
	}
	
	private void failure(String message)
	{
		// clear the fields
		emailInput.setText("");
		passwordInput.setText("");
		
		// done
		removeDialog(DIALOG_LOGGING_IN);
		
		// show the message
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		
		// and re-enable the button
		login.setEnabled(true);
		
		
	}
	
	private void success(JSONObject results)
	{
		// done
		String email = emailInput.getText().toString().trim();
		removeDialog(DIALOG_LOGGING_IN);

		JSONObject props = new JSONObject();
		
		// save the guid to the preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		SharedPreferences.Editor edit = prefs.edit();
		try{			
			edit.putString(getString(R.string.pref_guid), results.getString("guid"));
			edit.putString(getString(R.string.pref_name), results.getString("name"));
			edit.putString(getString(R.string.pref_email), email);
			
			if(results.has("partnerName")){
				edit.putString(getString(R.string.pref_coupled), results.getString("partnerEmail"));
				edit.putString(getString(R.string.pref_partner), results.getString("partnerGuid"));
				edit.putString(getString(R.string.pref_partner_name), results.getString("partnerName"));
				edit.putString(getString(R.string.pref_coupled_guid), results.getString("requestGuid"));
			}		
			edit.commit();
	
			props.put("guid", results.getString("guid"));
			props.put("name", results.getString("name"));
			props.put("email", email);
		}catch(Exception e1){}
		mMixpanel.track("Login", props);
		
		// start up the preferences screen
		//startActivity(new Intent(this, CouplePreferences.class));
		startActivity(new Intent(this, CoupleSettings.class));
		
		// we're done with this screen
		finish();	
	}
	
	private static final int SUCCESS = 1;
	private static final int FAILURE = 2;
	private Handler msgHandler = new Handler(){
		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what){
			case SUCCESS:
				success((JSONObject)msg.obj);
				break;
			case FAILURE:
				failure(msg.obj.toString());
				break;
			}
		}
	};
	
	public static final int DIALOG_LOGGING_IN = 1;

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch(id)
		{
		case DIALOG_LOGGING_IN:
		{
			ProgressDialog mDialog1 = new ProgressDialog(this);
			mDialog1.setIndeterminate(true);
			mDialog1.setCancelable(true);
			mDialog1.setTitle(getString(R.string.title_logging_in));
			mDialog1.setMessage(getString(R.string.message_logging_in));
			return mDialog1;
		}
		}
		return null;
	}
	
	@Override
	protected void onDestroy()
	{
		mMixpanel.flush();
		super.onDestroy();
		//App.nullViewDrawablesRecursive(findViewById(R.id.root));
	}
}
