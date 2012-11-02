package com.hipmob.android.couple;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.preference.Preference.OnPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.hipmob.android.HipmobCore;
import com.hipmob.android.HipmobRemoteConnection;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

/**
 * Use a ViewSwitcher below the EditText controls based on the states. - State
 * 0: unattached - Show the peer email input and the invite button
 * 
 * - State 1: Invite sent, waiting for acknowledgement - Show the email you
 * invited, the cancel button and a hamster on a tread mill
 * 
 * - State 2: Invite confirmed - Display the peer email address and the breakup
 * button
 * 
 * - State 3: Invite rejected - Show a sad face and an Invite someone else
 * button
 * 
 * The Update name/email/password input on save: - Password requires a confirm
 * with the current password - add a chat button to that title page
 */
public class CoupleSettings extends Activity implements OnClickListener
{
	private SharedPreferences prefs;
	private Preference name, email;
	private boolean changed;
	private Button chat, invite, cancel, breakup, inviteAnother, requestAnother, support;
	private ViewFlipper stages;
	private String settingName, settingEmail, settingPassword;

	private EditText emailInput, passwordInput, nameInput;
	private EditText coupleEmail, invitedEmail, partnerEmail, partnerDeclinedEmail, partnerCancelledEmail;
	private String guid;
	private String ua;
	private boolean initial;
	private static final String TAG = "CoupleSettings";

	private Handler msgHandler;
	
	private static final int SUCCESS_REQUEST = 1;
	private static final int FAILURE_REQUEST = 2;
	private static final int SUCCESS_CANCEL = 3;
	private static final int FAILURE_CANCEL = 4;
	private static final int SUCCESS_BREAKUP = 5;
	private static final int FAILURE_BREAKUP = 6;
	private static final int FAILURE_STATUS = 7;
	private static final int STATUS_CHECK = 8;
	private static final int SUCCESS_STATUS_ACCEPTED = 9;
	private static final int SUCCESS_STATUS_DECLINED = 10;
	private static final int SUCCESS_STATUS_REQUEST = 11;
	private static final int SUCCESS_STATUS_CANCELLED = 12;
	private static final int SUCCESS_STATUS_BROKENUP = 13;
	private static final int SUCCESS_ACCEPT = 14;
	private static final int FAILURE_ACCEPT = 15;
	private static final int SUCCESS_DECLINE = 16;
	private static final int FAILURE_DECLINE = 17;
	
	public static final Pattern EMAIL_ADDRESS = Pattern
			.compile("[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" + "\\@"
					+ "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" + "(" + "\\."
					+ "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" + ")+");

	private ArrayList<JSONArray> acceptList;
	private ArrayAdapter<JSONArray> acceptOptions;

	private static final long CHECK_INTERVAL = 30000;
	private static final long CHECK_INTERVAL_LONG = 60000;
	
	private MixpanelAPI mMixpanel;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		mMixpanel = MixpanelAPI.getInstance(this, App.MIXPANEL_ID);
		
		setContentView(R.layout.settings);
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		guid = prefs.getString(getString(R.string.pref_guid), "");
		
		try{
			JSONObject superProperties = new JSONObject();
			superProperties.put("guid", guid);
			mMixpanel.registerSuperProperties(superProperties);
		}catch(Exception e1){}
		
		stages = (ViewFlipper) findViewById(R.id.current_state);

		nameInput = (EditText) findViewById(R.id.name);
		settingName = prefs.getString(getString(R.string.pref_name), null);
		if (settingName != null)
			nameInput.setText(settingName);
		emailInput = (EditText) findViewById(R.id.email);
		settingEmail = prefs.getString(getString(R.string.pref_email), null);
		if (settingEmail != null)
			emailInput.setText(settingEmail);
		passwordInput = (EditText) findViewById(R.id.password);

		coupleEmail = (EditText) findViewById(R.id.couple_email);

		invitedEmail = (EditText) findViewById(R.id.invited_email);

		partnerEmail = (EditText) findViewById(R.id.partner_email);

		partnerDeclinedEmail = (EditText) findViewById(R.id.partner_declined_email);
		
		partnerCancelledEmail = (EditText) findViewById(R.id.partner_cancelled_email);

		invite = (Button) findViewById(R.id.invite);
		invite.setOnClickListener(this);
		cancel = (Button) findViewById(R.id.cancel_invitation);
		cancel.setOnClickListener(this);
		breakup = (Button) findViewById(R.id.breakup);
		breakup.setOnClickListener(this);
		inviteAnother = (Button) findViewById(R.id.invite_another);
		inviteAnother.setOnClickListener(this);
		requestAnother = (Button) findViewById(R.id.request_another);
		requestAnother.setOnClickListener(this);
		
		chat = (Button)findViewById(R.id.chat);
		chat.setOnClickListener(this);
		chat.setVisibility(View.GONE);
		
		support = (Button)findViewById(R.id.support);
		support.setOnClickListener(this);
		
		StringBuilder ua = new StringBuilder(1024);
		ua.append("Android/Couple 1.0; ");
		ua.append("OS Version: ").append(System.getProperty("os.version"))
				.append("(");
		ua.append(android.os.Build.VERSION.INCREMENTAL).append(");");
		ua.append("OS API Level: ").append(android.os.Build.VERSION.SDK_INT);
		ua.append(";Device: ").append(android.os.Build.DEVICE);
		ua.append("; Model: ").append(android.os.Build.MODEL);
		ua.append(" (").append(android.os.Build.PRODUCT).append(")");
		this.ua = ua.toString();
		
		msgHandler = new Handler() {
			@SuppressWarnings("unchecked")
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case SUCCESS_REQUEST:
					success_request((String[])msg.obj);
					break;
				case FAILURE_REQUEST:
					failure_request(msg.obj.toString());
					break;
				case SUCCESS_CANCEL:
					success_cancel();
					break;
				case FAILURE_CANCEL:
					failure_cancel(msg.obj.toString(), msg.arg1);
					break;
				case SUCCESS_BREAKUP:
					success_breakup();
					break;
				case FAILURE_BREAKUP:
					failure_breakup(msg.obj.toString());
					break;
				case STATUS_CHECK:
					this.sendEmptyMessageDelayed(STATUS_CHECK, CHECK_INTERVAL);
					checkStatus();
					break;
				case SUCCESS_STATUS_ACCEPTED:
					success_status_accepted((String[])msg.obj);
					break;
				case SUCCESS_STATUS_DECLINED:
					success_status_declined((String[])msg.obj);
					break;
				case SUCCESS_STATUS_CANCELLED:
					success_status_cancelled();
					break;
				case SUCCESS_STATUS_BROKENUP:
					success_status_brokenup();
					break;
				case SUCCESS_STATUS_REQUEST:
					success_status_request((JSONArray)msg.obj);
					break;
				case FAILURE_STATUS:
					failure_status(msg.obj.toString(), msg.arg1);
					break;
				case FAILURE_ACCEPT:
					failure_accept(msg.obj.toString());
					break;
				case FAILURE_DECLINE:
					failure_decline(msg.obj.toString());
					break;
				case SUCCESS_ACCEPT:
					success_accept((String[])msg.obj);
					break;
				case SUCCESS_DECLINE:
					success_decline();
					break;
				}
			}
		};
		
		// setup for the accept options
		acceptList = new ArrayList<JSONArray>(10);

		// create the appropriate adapter
		acceptOptions = new ArrayAdapter<JSONArray>(this, android.R.layout.simple_spinner_item, acceptList){
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				View v = convertView;
				if (v == null) {
					LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					v = vi.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
				}
				JSONArray ox = getItem(position);
				CheckedTextView ctv = (CheckedTextView)v.findViewById(android.R.id.text1);
				try{
					ctv.setText(ox.getString(2)+" <"+ox.getString(0)+">");
				}catch(Exception e1){}
				ctv.setSelected(false);
				return v;
			}
		};
		acceptOptions.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		
		initial = true;
	}

	@Override
	public void onPause() {
		super.onPause();
		
		// stop the success interval timer
		msgHandler.removeMessages(STATUS_CHECK);
	}
	
	@Override
	protected void onDestroy()
	{
		mMixpanel.flush();
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		configureStages(true);
	}

	/*
	 * 
	 * if(preference == name){ String value = newValue.toString().trim();
	 * if("".equals(value)){ Toast.makeText(this,
	 * getString(R.string.error_invalid_name), Toast.LENGTH_LONG).show(); }else{
	 * preference.setSummary(value); savePreference(preference, value);
	 * 
	 * Toast.makeText(this, getString(R.string.message_name_updated),
	 * Toast.LENGTH_LONG).show(); } }else if(preference == email){ // validate
	 * that it is an email address String value = newValue.toString().trim();
	 * if(EMAIL_ADDRESS.matcher(value).matches()){ preference.setSummary(value);
	 * savePreference(preference, value); Toast.makeText(this,
	 * getString(R.string.message_email_updated), Toast.LENGTH_LONG).show();
	 * }else{ Toast.makeText(this, getString(R.string.error_invalid_email),
	 * Toast.LENGTH_LONG).show(); } } return false; }
	 * 
	 * void savePreference(Preference preference, String value) {
	 * SharedPreferences.Editor edit = prefs.edit();
	 * edit.putString(preference.getKey(), value); edit.commit(); changed =
	 * true; }
	 */

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			// do something on back.
			if (changed) {
				/*
				 * Intent i = new Intent();
				 * i.setAction(App.NOTICE_PREFERENCES_CHANGED);
				 * i.putExtra(Intent.EXTRA_INTENT, App.UPDATE_PREFERENCES);
				 * sendBroadcast(i);
				 */
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	void startChat()
	{
		String email = prefs.getString(getString(R.string.pref_email), "");
		if ("".equals(email)) {
			Toast.makeText(this, getString(R.string.error_invalid_email),
					Toast.LENGTH_LONG).show();
			return;
		}

		String name = prefs.getString(getString(R.string.pref_name), "");
		if ("".equals(name)) {
			Toast.makeText(this, getString(R.string.error_invalid_name),
					Toast.LENGTH_LONG).show();
			return;
		}

		String peer = prefs.getString(getString(R.string.pref_partner), "");
		
		// start the chat
		Intent i = new Intent(this, HipmobCore.class);

		// REQUIRED: set the appid to the key you're provided
		i.putExtra(HipmobCore.KEY_APPID, App.HIPMOB_KEY);

		// provide the device identifier here (for use with API calls and
		// for peer-to-peer connections)
		i.putExtra(HipmobCore.KEY_DEVICEID, guid);

		// set the user's name here
		i.putExtra(HipmobCore.KEY_NAME, name);

		// put the user's email here (will show up in the chat status)
		i.putExtra(HipmobCore.KEY_EMAIL, email);

		// and finally the peer
		i.putExtra(HipmobCore.KEY_PEER, peer);
		
		// title
		i.putExtra(HipmobCore.KEY_TITLE, String.format(getString(R.string.title_chat), prefs.getString(getString(R.string.pref_partner_name), "")));
		
		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", email);
			props.put("peer", peer);
			props.put("peer name", name);
			props.put("peer email", prefs.getString(getString(R.string.pref_partner), ""));
		}catch(Exception e1){}
		mMixpanel.track("Start Chat", props);
		
		startActivity(i);
	}
	
	void startSupportChat()
	{
		String email = prefs.getString(getString(R.string.pref_email), "");
		if ("".equals(email)) {
			Toast.makeText(this, getString(R.string.error_invalid_email),
					Toast.LENGTH_LONG).show();
			return;
		}

		String name = prefs.getString(getString(R.string.pref_name), "");
		if ("".equals(name)) {
			Toast.makeText(this, getString(R.string.error_invalid_name),
					Toast.LENGTH_LONG).show();
			return;
		}

		// start the chat
		Intent i = new Intent(this, HipmobCore.class);

		// REQUIRED: set the appid to the key you're provided
		i.putExtra(HipmobCore.KEY_APPID, App.HIPMOB_KEY);

		// provide the device identifier here (for use with API calls and
		// for peer-to-peer connections)
		i.putExtra(HipmobCore.KEY_DEVICEID, guid);

		// set the user's name here
		i.putExtra(HipmobCore.KEY_NAME, name);

		// put the user's email here (will show up in the chat status)
		i.putExtra(HipmobCore.KEY_EMAIL, email);
		
		// title
		i.putExtra(HipmobCore.KEY_TITLE, getString(R.string.title_support_chat));
		
		JSONObject props = new JSONObject();
		try{
			props.put("name", name);
			props.put("email", email);
		}catch(Exception e1){}
		mMixpanel.track("Start Support Chat", props);
		
		startActivity(i);
	}
	
	@Override
	public void onClick(View arg0) {
		if (chat == arg0) {
			startChat();
		}else if(support == arg0){
			startSupportChat();
		} else if (invite == arg0) {
			// got them both: run the invitation request
			String email = coupleEmail.getText().toString().trim();
			
			if("".equals(email) || !App.EMAIL_ADDRESS.matcher(email).matches()){
				Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_LONG).show();
				return;
			}
			
			arg0.setEnabled(false);
			
			new Thread(new Request(guid, email)).start();
			
			showDialog(DIALOG_REQUEST);
		} else if (inviteAnother == arg0) {
			arg0.setEnabled(false);
			
			SharedPreferences.Editor edit = prefs.edit();
			edit.remove(getString(R.string.pref_declined));
			edit.commit();

			configureStages(false);
			arg0.setEnabled(true);
		} else if (requestAnother == arg0) {
			arg0.setEnabled(false);
			
			SharedPreferences.Editor edit = prefs.edit();
			edit.remove(getString(R.string.pref_cancelled));
			edit.remove(getString(R.string.pref_brokenup));
			edit.commit();

			configureStages(false);
			arg0.setEnabled(true);
		} else if (cancel == arg0) {
			// request
			arg0.setEnabled(false);

			new Thread(new CancelRequest(guid, prefs.getString(getString(R.string.pref_invited_guid), ""))).start();
			
			showDialog(DIALOG_CANCEL);
		} else if (breakup == arg0) {
			// request
			arg0.setEnabled(false);

			new Thread(new Breakup(guid, prefs.getString(getString(R.string.pref_coupled_guid), ""))).start();
			
			showDialog(DIALOG_BREAKUP);
		}
	}

	void configureStages(boolean init) {
		int id = R.id.stage_0;
		if (prefs.contains(getString(R.string.pref_invited))) {
			id = R.id.stage_1;
			invitedEmail.setText(prefs.getString(
					getString(R.string.pref_invited), ""));
			
			if(init) checkStatus();
		} else if (prefs.contains(getString(R.string.pref_coupled))) {
			id = R.id.stage_2;
			partnerEmail.setText(prefs.getString(
					getString(R.string.pref_coupled), ""));
		} else if (prefs.contains(getString(R.string.pref_declined))) {
			id = R.id.stage_3;
			partnerDeclinedEmail.setText(prefs.getString(
					getString(R.string.pref_declined), ""));
		} else if (prefs.contains(getString(R.string.pref_cancelled))) {
			id = R.id.stage_4;
			partnerCancelledEmail.setText(prefs.getString(
					getString(R.string.pref_cancelled), ""));
		} else if (prefs.contains(getString(R.string.pref_brokenup))) {
			id = R.id.stage_4;
			partnerCancelledEmail.setText(prefs.getString(
					getString(R.string.pref_brokenup), ""));
		}else{
			if(init) checkStatus();
		}

		msgHandler.removeMessages(STATUS_CHECK);
		msgHandler.sendEmptyMessageDelayed(STATUS_CHECK, CHECK_INTERVAL);
		
		if(id == R.id.stage_2){
			chat.setVisibility(View.VISIBLE);
		}else{
			chat.setVisibility(View.GONE);
		}
		
		// move to the appropriate view
		while (stages.getCurrentView().getId() != id) {
			stages.showNext();
		}

		if (id == R.id.stage_0) {
			partnerEmail.requestFocus();
		}
		
		if(initial){
			initial = false;
			if(id == R.id.stage_2){
				startChat();
			}
		}
	}

	public static final int DIALOG_REQUEST = 1;
	public static final int DIALOG_BREAKUP = 2;
	public static final int DIALOG_CANCEL = 3;
	public static final int DIALOG_SELECT = 4;
	public static final int DIALOG_ACCEPT = 5;
	public static final int DIALOG_DECLINE = 6;
	

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_REQUEST: {
			ProgressDialog mDialog1 = new ProgressDialog(this);
			mDialog1.setIndeterminate(true);
			mDialog1.setCancelable(true);
			mDialog1.setTitle(getString(R.string.title_requesting_couple));
			mDialog1.setMessage(getString(R.string.title_requesting_couple));
			return mDialog1;
		}
		case DIALOG_CANCEL: {
			ProgressDialog mDialog1 = new ProgressDialog(this);
			mDialog1.setIndeterminate(true);
			mDialog1.setCancelable(true);
			mDialog1.setTitle(getString(R.string.title_cancelling_request));
			mDialog1.setMessage(getString(R.string.title_cancelling_request));
			return mDialog1;
		}
		case DIALOG_BREAKUP: {
			ProgressDialog mDialog1 = new ProgressDialog(this);
			mDialog1.setIndeterminate(true);
			mDialog1.setCancelable(true);
			mDialog1.setTitle(getString(R.string.title_request_breakup));
			mDialog1.setMessage(getString(R.string.title_request_breakup));
			return mDialog1;
		}
		case DIALOG_ACCEPT: {
			ProgressDialog mDialog1 = new ProgressDialog(this);
			mDialog1.setIndeterminate(true);
			mDialog1.setCancelable(true);
			mDialog1.setTitle(getString(R.string.title_accepting_request));
			mDialog1.setMessage(getString(R.string.title_accepting_request));
			return mDialog1;
		}
		case DIALOG_DECLINE: {
			ProgressDialog mDialog1 = new ProgressDialog(this);
			mDialog1.setIndeterminate(true);
			mDialog1.setCancelable(true);
			mDialog1.setTitle(getString(R.string.title_declining_request));
			mDialog1.setMessage(getString(R.string.title_declining_request));
			return mDialog1;
		}
		case DIALOG_SELECT: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.title_select_couple));
			if(acceptOptions.getCount() == 2){
				final JSONArray opt = acceptOptions.getItem(1);
				String msg = "";
				try{ msg = String.format(getString(R.string.label_couple_request), opt.getString(2), opt.getString(0)); }catch(Exception e1){}
				return builder.setMessage(msg).setPositiveButton(getString(R.string.button_accept),
						new DialogInterface.OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								removeDialog(DIALOG_SELECT);
								acceptCouple(opt);
							}
						}).setNegativeButton(getString(R.string.button_decline),
						new DialogInterface.OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								removeDialog(DIALOG_SELECT);
								rejectCouple();
							}
						}).create();
			}else{
				return builder.setSingleChoiceItems(acceptOptions, -1, new DialogInterface.OnClickListener(){
		    		public void onClick(DialogInterface dialog, int item){
		    			selectCouple(item);
		    		}
		    	}).create();
			}
		}
		}
		return null;
	}

	void acceptCouple(JSONArray opt)
	{
		try{
			// 	accepted the specified one and cancels all the others
			new Thread(new Accept(guid, opt.getString(1), opt.getString(2))).start();
		
			showDialog(DIALOG_ACCEPT);
		}catch(Exception e1){
			
		}
	}
	
	void rejectCouple()
	{
		// cancelled all
		new Thread(new Decline(guid, "all")).start();
		
		showDialog(DIALOG_DECLINE);		
	}
	
	void selectCouple(int item)
	{
		removeDialog(DIALOG_SELECT);
		JSONArray opt = acceptOptions.getItem(item);
		if(opt.length() == 1){
			rejectCouple();
		}else{
			acceptCouple(opt);
		}
	}
	
	private void failure_request(String message) {
		// done
		removeDialog(DIALOG_REQUEST);

		// show the message
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		
		invite.setEnabled(true);
	}

	private void success_request(String[] details) {
		// done
		removeDialog(DIALOG_REQUEST);

		// clear the input
		coupleEmail.setText("");
		
		// save the guid to the preferences
		SharedPreferences.Editor edit = prefs.edit();
		edit.putString(getString(R.string.pref_invited), details[0]);
		edit.putString(getString(R.string.pref_invited_guid), details[1]);
		edit.commit();

		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", prefs.getString(getString(R.string.pref_email), ""));
			props.put("invited", details[1]);
		}catch(Exception e1){}
		mMixpanel.track("Invitation", props);
		
		invite.setEnabled(true);
		configureStages(false);
	}

	private void failure_cancel(String message, int reset) {
		// done
		removeDialog(DIALOG_CANCEL);

		// show the message
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		
		cancel.setEnabled(true);
		if(reset == 1){
			// save the guid to the preferences
			SharedPreferences.Editor edit = prefs.edit();
			edit.remove(getString(R.string.pref_invited));
			edit.remove(getString(R.string.pref_invited_guid));
			edit.commit();
			
			configureStages(false);			
		}
	}

	private void success_cancel() {
		// done
		removeDialog(DIALOG_CANCEL);

		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", prefs.getString(getString(R.string.pref_email), ""));
			props.put("invited", prefs.getString(getString(R.string.pref_invited), ""));
		}catch(Exception e1){}
		mMixpanel.track("Cancelled", props);
		
		// save the guid to the preferences
		SharedPreferences.Editor edit = prefs.edit();
		edit.remove(getString(R.string.pref_invited));
		edit.remove(getString(R.string.pref_invited_guid));
		edit.commit();

		cancel.setEnabled(true);
		configureStages(false);
	}

	private void failure_breakup(String message) {
		// done
		removeDialog(DIALOG_BREAKUP);

		// show the message
		breakup.setEnabled(true);
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	private void success_breakup() {
		// done
		removeDialog(DIALOG_BREAKUP);

		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", prefs.getString(getString(R.string.pref_email), ""));
			props.put("previous partner email", prefs.getString(getString(R.string.pref_coupled), ""));
			props.put("previous partner guid", prefs.getString(getString(R.string.pref_partner), ""));
			props.put("previous partner name", prefs.getString(getString(R.string.pref_partner_name), ""));
		}catch(Exception e1){}
		mMixpanel.track("We Broke Up", props);
		
		// save the guid to the preferences
		SharedPreferences.Editor edit = prefs.edit();
		edit.remove(getString(R.string.pref_coupled));
		edit.remove(getString(R.string.pref_partner));
		edit.remove(getString(R.string.pref_partner_name));
		edit.remove(getString(R.string.pref_coupled_guid));
		edit.commit();

		breakup.setEnabled(true);
		configureStages(false);
	}

	class Request implements Runnable {
		private String guid, partnerEmail;

		Request(String guid, String partnerEmail) {
			this.guid = guid;
			this.partnerEmail = partnerEmail;
		}

		public void run() {
			Message m = Message.obtain();
			m.what = FAILURE_REQUEST;
			m.obj = CoupleSettings.this
					.getString(R.string.error_unknown);
			try {
				// lets run the actual request
				StringBuilder sb = new StringBuilder(300);
				sb.append("guid=").append(URLEncoder.encode(guid));
				sb.append("&partnerEmail=").append(
						URLEncoder.encode(partnerEmail));

				String res = App.executePost(
						App.SERVER_URI + "partner/request", sb.toString(), ua);
				if (res != null) {
					JSONObject detail = (JSONObject) new JSONTokener(res)
							.nextValue();
					if (detail.has("error")) {
						m.what = FAILURE_REQUEST;
						m.obj = detail.getString("error");
					} else if (detail.has("success")) {
						m.what = SUCCESS_REQUEST;
						m.obj = new String[]{ detail.getString("success"), detail.getString("guid") };
					}
				}
			} catch (JSONException jse) {
				m.what = FAILURE_REQUEST;
				m.obj = CoupleSettings.this
						.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["
						+ jse.getMessage() + "]", jse);
			} catch (IOException ioe) {
				m.what = FAILURE_REQUEST;
				m.obj = CoupleSettings.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG,
						"Exception logging in [" + ioe.getMessage() + "]", ioe);
			}
			msgHandler.sendMessageDelayed(m, 10);
		}
	}

	class CancelRequest implements Runnable {
		private String guid, requestGuid;

		CancelRequest(String guid, String requestGuid) {
			this.guid = guid;
			this.requestGuid = requestGuid;
		}

		public void run() {
			Message m = Message.obtain();
			m.what = FAILURE_CANCEL;
			m.obj = CoupleSettings.this
					.getString(R.string.error_unknown);
			try {
				// lets run the actual request
				StringBuilder sb = new StringBuilder(300);
				sb.append("guid=").append(URLEncoder.encode(guid));
				sb.append("&partnerGuid=").append(URLEncoder.encode(requestGuid));

				String res = App.executePost(App.SERVER_URI
						+ "partner/cancelrequest", sb.toString(), ua);
				if (res != null) {
					JSONObject detail = (JSONObject) new JSONTokener(res)
							.nextValue();
					if (detail.has("error")) {
						m.what = FAILURE_CANCEL;
						m.obj = detail.getString("error");
						if(detail.has("reset")){
							m.arg1 = 1;
						}
					} else if (detail.has("success")) {
						m.what = SUCCESS_CANCEL;
					}
				}
			} catch (JSONException jse) {
				m.what = FAILURE_CANCEL;
				m.obj = CoupleSettings.this
						.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["
						+ jse.getMessage() + "]", jse);
			} catch (IOException ioe) {
				m.what = FAILURE_CANCEL;
				m.obj = CoupleSettings.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG,
						"Exception logging in [" + ioe.getMessage() + "]", ioe);
			}
			msgHandler.sendMessageDelayed(m, 10);
		}
	}

	class Breakup implements Runnable {
		private String guid, requestGuid;

		Breakup(String guid, String requestGuid) {
			this.guid = guid;
			this.requestGuid = requestGuid;
		}

		public void run() {
			Message m = Message.obtain();
			m.what = FAILURE_BREAKUP;
			m.obj = CoupleSettings.this
					.getString(R.string.error_unknown);
			try {
				// lets run the actual request
				StringBuilder sb = new StringBuilder(300);
				sb.append("guid=").append(URLEncoder.encode(guid));
				sb.append("&partnerGuid=").append(URLEncoder.encode(requestGuid));
				
				String res = App.executePost(
						App.SERVER_URI + "partner/breakup", sb.toString(), ua);
				if (res != null) {
					JSONObject detail = (JSONObject) new JSONTokener(res)
							.nextValue();
					if (detail.has("error")) {
						m.what = FAILURE_BREAKUP;
						m.obj = detail.getString("error");
					} else if (detail.has("success")) {
						m.what = SUCCESS_BREAKUP;
					}
				}
			} catch (JSONException jse) {
				m.what = FAILURE_BREAKUP;
				m.obj = CoupleSettings.this
						.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["
						+ jse.getMessage() + "]", jse);
			} catch (IOException ioe) {
				m.what = FAILURE_BREAKUP;
				m.obj = CoupleSettings.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG,
						"Exception logging in [" + ioe.getMessage() + "]", ioe);
			}
			msgHandler.sendMessageDelayed(m, 10);
		}
	}
	
	void checkStatus()
	{
		if(prefs.contains(getString(R.string.pref_invited_guid))){
			new Thread(new Status(guid, prefs.getString(getString(R.string.pref_invited_guid), ""), false)).start();
		}else if(prefs.contains(getString(R.string.pref_coupled_guid))){
			new Thread(new Status(guid, prefs.getString(getString(R.string.pref_coupled_guid), ""), true)).start();
		}else{
			new Thread(new Status(guid, "", false)).start();
		}
	}
	
	class Status implements Runnable {
		private String guid, requestGuid;
		private boolean active;

		Status(String guid, String requestGuid, boolean active)
		{
			this.guid = guid;
			this.requestGuid = requestGuid;
			this.active = active;
		}

		public void run() {
			Message m = null;
			try {
				// lets run the actual request
				StringBuilder sb = new StringBuilder(300);
				sb.append("guid=").append(URLEncoder.encode(guid));
				if(!"".equals(requestGuid)) sb.append("&partnerGuid=").append(URLEncoder.encode(requestGuid));
				if(active) sb.append("&active=true");
				
				String res = App.executePost(
						App.SERVER_URI + "partner/status", sb.toString(), ua);
				if (res != null) {
 					JSONObject detail = (JSONObject) new JSONTokener(res)
							.nextValue();
					if (detail.has("error")) {
						if(m == null){
							m = Message.obtain();
							m.what = FAILURE_STATUS;
							m.obj = CoupleSettings.this
									.getString(R.string.error_unknown);
							m.arg1 = 0;
						}
						m.what = FAILURE_STATUS;
						m.obj = detail.getString("error");
						if(detail.has("reset")){
							m.arg1 = 1;
						}
					} else if (detail.has("accepted")){
						if(detail.has("partnerEmail") && detail.has("partnerGuid") && detail.has("partnerName")) {
							if(m == null){
								m = Message.obtain();
								m.what = FAILURE_STATUS;
								m.obj = CoupleSettings.this
										.getString(R.string.error_unknown);
								m.arg1 = 0;
							}
							m.what = SUCCESS_STATUS_ACCEPTED;
							m.obj = new String[]{ detail.getString("partnerEmail"), detail.getString("partnerGuid"), detail.getString("partnerName") };
						}
					} else if (detail.has("declined")){
						if(detail.has("partnerEmail")){
							if(m == null){
								m = Message.obtain();
								m.what = FAILURE_STATUS;
								m.obj = CoupleSettings.this
										.getString(R.string.error_unknown);
								m.arg1 = 0;
							}
							m.what = SUCCESS_STATUS_DECLINED;
							m.obj = new String[]{ detail.getString("partnerEmail") };
						}
					} else if (detail.has("brokenup")){
						if(m == null){
							m = Message.obtain();
							m.what = FAILURE_STATUS;
							m.obj = CoupleSettings.this
									.getString(R.string.error_unknown);
							m.arg1 = 0;
						}
						m.what = SUCCESS_STATUS_BROKENUP;
					} else if (detail.has("cancelled")){
						if(m == null){
							m = Message.obtain();
							m.what = FAILURE_STATUS;
							m.obj = CoupleSettings.this
									.getString(R.string.error_unknown);
							m.arg1 = 0;
						}
						m.what = SUCCESS_STATUS_CANCELLED;
					}else if(detail.has("pending") || detail.has("active") || detail.has("norequests")){
						return;
					}else if(detail.has("request")){
						if(m == null){
							m = Message.obtain();
							m.what = FAILURE_STATUS;
							m.obj = CoupleSettings.this
									.getString(R.string.error_unknown);
							m.arg1 = 0;
						}
						m.what = SUCCESS_STATUS_REQUEST;
						m.obj = detail.getJSONArray("options");
					}else{
						// nothing doing: just ignore
						return;
					}
				}
			} catch (JSONException jse) {
				if(m == null){
					m = Message.obtain();
					m.what = FAILURE_STATUS;
					m.obj = CoupleSettings.this
							.getString(R.string.error_unknown);
					m.arg1 = 0;
				}
				m.what = FAILURE_STATUS;
				m.obj = CoupleSettings.this
						.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["
						+ jse.getMessage() + "]", jse);
			} catch (IOException ioe) {
				if(m == null){
					m = Message.obtain();
					m.what = FAILURE_STATUS;
					m.obj = CoupleSettings.this
							.getString(R.string.error_unknown);
					m.arg1 = 0;
				}
				m.what = FAILURE_STATUS;
				m.obj = CoupleSettings.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG,
						"Exception logging in [" + ioe.getMessage() + "]", ioe);
			}
			if(m == null) return;
			msgHandler.sendMessageDelayed(m, 10);
		}
	}
	
	private void failure_status(String message, int reset)
	{
		// show the message
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		
		if(reset == 1){
			// save the guid to the preferences
			SharedPreferences.Editor edit = prefs.edit();
			edit.remove(getString(R.string.pref_invited));
			edit.commit();
			
			configureStages(false);			
		}
	}

	private void success_status_accepted(String[] partnerInfo)
	{
		SharedPreferences.Editor edit = prefs.edit();
		
		// we now have a partner
		edit.putString(getString(R.string.pref_coupled), partnerInfo[0]);
		edit.putString(getString(R.string.pref_partner), partnerInfo[1]);
		edit.putString(getString(R.string.pref_coupled_guid), prefs.getString(getString(R.string.pref_invited_guid), ""));
		edit.putString(getString(R.string.pref_partner_name), partnerInfo[2]);
		
		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", prefs.getString(getString(R.string.pref_email), ""));
			props.put("partner email", partnerInfo[0]);
			props.put("partner guid", partnerInfo[1]);
			props.put("partner name", partnerInfo[2]);
		}catch(Exception e1){}
		mMixpanel.track("They Accepted", props);
		
		edit.remove(getString(R.string.pref_invited));
		edit.remove(getString(R.string.pref_invited_guid));
		edit.remove(getString(R.string.pref_declined));
		edit.remove(getString(R.string.pref_cancelled));
		edit.commit();
		
		configureStages(false);
	}
	
	private void success_status_cancelled()
	{
		SharedPreferences.Editor edit = prefs.edit();
		
		// we no longer have a partner
		edit.putString(getString(R.string.pref_cancelled), prefs.getString(getString(R.string.pref_coupled), prefs.getString(getString(R.string.pref_invited), "")));
		
		edit.remove(getString(R.string.pref_invited));
		edit.remove(getString(R.string.pref_invited_guid));
		edit.remove(getString(R.string.pref_coupled));
		edit.remove(getString(R.string.pref_partner));
		edit.remove(getString(R.string.pref_partner_name));
		edit.remove(getString(R.string.pref_coupled_guid));
		
		edit.commit();
		
		configureStages(false);
	}
	
	private void success_status_brokenup()
	{
		SharedPreferences.Editor edit = prefs.edit();
		
		// we no longer have a partner, and they did it
		edit.putString(getString(R.string.pref_brokenup), prefs.getString(getString(R.string.pref_coupled), prefs.getString(getString(R.string.pref_invited), "")));

		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", prefs.getString(getString(R.string.pref_email), ""));
			props.put("previous partner email", prefs.getString(getString(R.string.pref_coupled), ""));
			props.put("previous partner guid", prefs.getString(getString(R.string.pref_partner), ""));
			props.put("previous partner name", prefs.getString(getString(R.string.pref_partner_name), ""));
		}catch(Exception e1){}
		mMixpanel.track("They Broke Up", props);
		
		edit.remove(getString(R.string.pref_invited));
		edit.remove(getString(R.string.pref_invited_guid));
		edit.remove(getString(R.string.pref_coupled));
		edit.remove(getString(R.string.pref_partner));
		edit.remove(getString(R.string.pref_partner_name));
		edit.remove(getString(R.string.pref_coupled_guid));
		
		edit.commit();
		
		configureStages(false);
	}
	
	private void success_status_declined(String[] partnerInfo)
	{
		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", prefs.getString(getString(R.string.pref_email), ""));
			props.put("attempted partner email", prefs.getString(getString(R.string.pref_invited), ""));
		}catch(Exception e1){}
		mMixpanel.track("They Declined", props);
		
		SharedPreferences.Editor edit = prefs.edit();
		edit.remove(getString(R.string.pref_invited));
		edit.remove(getString(R.string.pref_invited_guid));
		edit.putString(getString(R.string.pref_declined), partnerInfo[0]);
		edit.commit();
		configureStages(false);
	}
	
	private void success_status_request(JSONArray options)
	{
		acceptList.clear();
		JSONArray arr = new JSONArray();
		arr.put(getString(R.string.option_decline_all));
		acceptList.add(arr);
		int i, l = options.length();
		try{
			for(i=0;i<l;i++){
				acceptList.add(options.getJSONArray(i));
			}
		}catch(JSONException e1){}
		showDialog(DIALOG_SELECT);
	}
	
	class Accept implements Runnable {
		private String guid, requestGuid, name;

		Accept(String guid, String requestGuid, String name){
			this.guid = guid;
			this.requestGuid = requestGuid;
			this.name = name;
		}

		public void run() {
			Message m = Message.obtain();
			m.what = FAILURE_ACCEPT;
			m.obj = CoupleSettings.this
					.getString(R.string.error_unknown);
			try {
				// lets run the actual request
				StringBuilder sb = new StringBuilder(300);
				sb.append("guid=").append(URLEncoder.encode(guid));
				sb.append("&partnerGuid=").append(URLEncoder.encode(requestGuid));
				
				String res = App.executePost(
						App.SERVER_URI + "partner/accept", sb.toString(), ua);
				if (res != null) {
					JSONObject detail = (JSONObject) new JSONTokener(res)
							.nextValue();
					if (detail.has("error")) {
						m.obj = detail.getString("error");
					} else if (detail.has("accepted")) {
						if(detail.has("partnerEmail") && detail.has("partnerGuid")) {
							m.what = SUCCESS_ACCEPT;
							m.obj = new String[]{ detail.getString("partnerEmail"), detail.getString("partnerGuid"), requestGuid, name };
						}
					}
				}
			} catch (JSONException jse) {
				m.obj = CoupleSettings.this
						.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["
						+ jse.getMessage() + "]", jse);
			} catch (IOException ioe) {
				m.obj = CoupleSettings.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG,
						"Exception logging in [" + ioe.getMessage() + "]", ioe);
			}
			msgHandler.sendMessageDelayed(m, 10);
		}
	}
	
	class Decline implements Runnable {
		private String guid, requestGuid;

		Decline(String guid, String requestGuid){
			this.guid = guid;
			this.requestGuid = requestGuid;
		}

		public void run() {
			Message m = Message.obtain();
			m.what = FAILURE_DECLINE;
			m.obj = CoupleSettings.this
					.getString(R.string.error_unknown);
			try {
				// lets run the actual request
				StringBuilder sb = new StringBuilder(300);
				sb.append("guid=").append(URLEncoder.encode(guid));
				sb.append("&partnerGuid=").append(URLEncoder.encode(requestGuid));
				
				String res = App.executePost(
						App.SERVER_URI + "partner/decline", sb.toString(), ua);
				if (res != null) {
					JSONObject detail = (JSONObject) new JSONTokener(res)
							.nextValue();
					if (detail.has("error")) {
						m.obj = detail.getString("error");
					} else if (detail.has("declined")) {
						m.what = SUCCESS_DECLINE;
					}
				}
			} catch (JSONException jse) {
				m.obj = CoupleSettings.this
						.getString(R.string.error_invalid_server_response);
				msgHandler.sendMessageDelayed(m, 10);
				android.util.Log.e(TAG, "Exception parsing server response ["
						+ jse.getMessage() + "]", jse);
			} catch (IOException ioe) {
				m.obj = CoupleSettings.this.getString(R.string.error_unknown);
				android.util.Log.e(TAG,
						"Exception logging in [" + ioe.getMessage() + "]", ioe);
			}
			msgHandler.sendMessageDelayed(m, 10);
		}
	}
	
	void failure_accept(String message) {
		// done
		removeDialog(DIALOG_ACCEPT);

		// show the message
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	
	void failure_decline(String message)
	{
		// done
		removeDialog(DIALOG_DECLINE);

		// show the message
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	
	void success_decline()
	{
		// done
		removeDialog(DIALOG_DECLINE);	
	}
	
	private void success_accept(String[] partnerInfo)
	{
		// done
		removeDialog(DIALOG_ACCEPT);

		SharedPreferences.Editor edit = prefs.edit();
		
		// we now have a partner
		edit.putString(getString(R.string.pref_coupled), partnerInfo[0]);
		edit.putString(getString(R.string.pref_partner), partnerInfo[1]);
		edit.putString(getString(R.string.pref_coupled_guid), partnerInfo[2]);
		edit.putString(getString(R.string.pref_partner_name), partnerInfo[3]);

		JSONObject props = new JSONObject();
		try{
			props.put("name", prefs.getString(getString(R.string.pref_name), ""));
			props.put("email", prefs.getString(getString(R.string.pref_email), ""));
			props.put("partner email", partnerInfo[0]);
			props.put("partner guid", partnerInfo[1]);
			props.put("partner name", partnerInfo[3]);
		}catch(Exception e1){}
		mMixpanel.track("We Accepted", props);
		
		edit.remove(getString(R.string.pref_invited));
		edit.remove(getString(R.string.pref_invited_guid));
		edit.commit();
		
		configureStages(false);
	}
	
	@Override 
	public void onConfigurationChanged(Configuration newConfig) 
	{ 
		super.onConfigurationChanged(newConfig);
	} 
}
