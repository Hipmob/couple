package com.hipmob.android.couple;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.UUID;

import android.os.Handler;
import android.os.Message;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
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
import com.mixpanel.android.mpmetrics.MixpanelAPI;

public class CoupleSignup extends Activity implements OnClickListener 
{	
	private Button signup;
	private EditText emailInput, passwordInput, nameInput;
	private TextView login;

	private static final String TAG = "CoupleLogin";
	
	private MixpanelAPI mMixpanel;
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		// remove the title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		
		mMixpanel = MixpanelAPI.getInstance(this, App.MIXPANEL_ID);
		
		// see if we already have a login
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		if(prefs.contains(getString(R.string.pref_guid))){
			startActivity(new Intent(this, CoupleSettings.class));
			
			finish();
			return;
		}
		// load the content view
		setContentView(R.layout.signup);
		
		login = (TextView)findViewById(R.id.login);
		login.setPaintFlags(login.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
		login.setOnClickListener(this);
		signup = (Button)findViewById(R.id.signup);
		signup.setOnClickListener(this);
		
		emailInput = (EditText)findViewById(R.id.email);
		passwordInput = (EditText)findViewById(R.id.password);
		nameInput = (EditText)findViewById(R.id.name);
		nameInput.requestFocus(); 
	}

	@Override
	public void onClick(View arg0)
	{
		if(login == arg0){
			// start up the signup screen
			startActivity(new Intent(this, CoupleLogin.class));
			
			// we're done with this screen
			finish();			
		}else if(signup == arg0){
			// check that we have a valid email address
			String name = nameInput.getText().toString().trim();
			String email = emailInput.getText().toString().trim();
			String password= passwordInput.getText().toString().trim();
			
			if("".equals(name)){
				Toast.makeText(this, getString(R.string.error_invalid_name), Toast.LENGTH_LONG).show();
				return;
			}
			
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
			
			new Thread(new Signup(email, password, name)).start();
			
			// start the progress bar
			showDialog(DIALOG_SIGNING_UP);
		}
	}
	
	class Signup implements Runnable
	{
		private String email, password, name;
		Signup(String email, String password, String name)
		{
			this.email = email;
			this.password = password;
			this.name = name;
		}
		
		public void run()
		{
			Message m = Message.obtain();
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
				sb.append("&name=").append(URLEncoder.encode(name));
				
				String res = App.executePost(App.SERVER_URI+"signup", sb.toString(), ua.toString());
				if(res != null){
					JSONObject detail = (JSONObject) new JSONTokener(res).nextValue();
					if(detail.has("error")){
						m.what = FAILURE;
						m.obj = detail.getString("error");
					}else if(detail.has("guid")){
						m.what = SUCCESS;
						m.obj = detail.getString("guid");
					}else{
						m.what = FAILURE;
						m.obj = CoupleSignup.this.getString(R.string.error_unknown);
					}
				}
			}catch(JSONException jse){
				m.what = FAILURE;
				m.obj = CoupleSignup.this.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["+jse.getMessage()+"]", jse);
			}catch(IOException ioe){
				m.what = FAILURE;
				m.obj = CoupleSignup.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG, "Exception logging in ["+ioe.getMessage()+"]", ioe);
			}
			msgHandler.sendMessageDelayed(m, 10);
		}
	}
	
	private void failure(String message)
	{	
		// done
		removeDialog(DIALOG_SIGNING_UP);
				
		// show the message
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		
		// and re-enable the button
		signup.setEnabled(true);
	}
	
	private void success(String guid)
	{
		// done
		String email = emailInput.getText().toString().trim();
		String name = nameInput.getText().toString().trim();
		removeDialog(DIALOG_SIGNING_UP);

		// save the guid to the preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		SharedPreferences.Editor edit = prefs.edit();
		edit.putString(getString(R.string.pref_guid), guid);
		edit.putString(getString(R.string.pref_name), name);
		edit.putString(getString(R.string.pref_email), email);
		edit.commit();

		JSONObject props = new JSONObject();
		try{
			props.put("guid", guid);
			props.put("name", name);
			props.put("email", email);
		}catch(Exception e1){}
		mMixpanel.track("Sign Up", props);
		
		// start up the preferences screen
		//startActivity(new Intent(this, CouplePreferences.class));
		startActivity(new Intent(this, CoupleSettings.class));
		
		// we're done with this screen
		finish();
	}
	
	private static final int SUCCESS = 0;
	private static final int FAILURE = 1;
	private Handler msgHandler = new Handler(){
		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what){
			case SUCCESS:
				success(msg.obj.toString());
				break;
			case FAILURE:
				failure(msg.obj.toString());
				break;
			}
		}
	};
	
	public static final int DIALOG_SIGNING_UP = 1;

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch(id)
		{
		case DIALOG_SIGNING_UP:
		{
			ProgressDialog mDialog1 = new ProgressDialog(this);
			mDialog1.setIndeterminate(true);
			mDialog1.setCancelable(true);
			mDialog1.setTitle(getString(R.string.title_signing_up));
			mDialog1.setMessage(getString(R.string.message_signing_up));
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
