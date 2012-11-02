package com.hipmob.android.couple;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

import android.app.Application;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class App extends Application 
{
	public static final String HIPMOB_KEY = "f6d01cf97f104910b4cf4d3b5eb26ad7";
	
	public static final String SERVER_URI = "https://couple.herokuapp.com/";
	
	public static final String SENDER_ID = "463956399310";
	
	public static final String MIXPANEL_ID = "b23daa80998e71482413a9fe6509434b";
	
	public static final Pattern EMAIL_ADDRESS
    = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
        "\\@" +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
        "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
        ")+"
    );
	
	public App()
	{
	}
	
	public static void nullViewDrawable(View view)
	{
		Drawable b;
		Drawable[] c;
		try{
			b = view.getBackground();
			if(b != null){
				b.setCallback(null);
				view.setBackgroundDrawable(null);
				if(BitmapDrawable.class.isInstance(b)) ((BitmapDrawable)b).getBitmap().recycle();
			}
		}catch(Exception e){}

		if(TextView.class.isInstance(view)){
			TextView tv = (TextView)view;
			c = tv.getCompoundDrawables();
			tv.setCompoundDrawables(null, null, null, null);
			for(int i=0;i<c.length;i++){
				if(c[i] != null){
					c[i].setCallback(null);
					if(BitmapDrawable.class.isInstance(c[i])) ((BitmapDrawable)c[i]).getBitmap().recycle();
				}
			}
		}
		
		if(ImageView.class.isInstance(view)){
			ImageView imageView = (ImageView)view;
			b = imageView.getDrawable();
			if(b != null){
				b.setCallback(null);
				imageView.setImageDrawable(null);
				if(BitmapDrawable.class.isInstance(b)) ((BitmapDrawable)b).getBitmap().recycle();
			}
			
		}
		
		if(ImageButton.class.isInstance(view)){
			ImageButton imageView = (ImageButton)view;
			b = imageView.getDrawable();
			if(b != null){
				b.setCallback(null);
				imageView.setImageDrawable(null);
				if(BitmapDrawable.class.isInstance(b)) ((BitmapDrawable)b).getBitmap().recycle();
			}
		}
	}
    
	public static void nullViewDrawablesRecursive(View view)
	{
		if(view != null){
			try{
				ViewGroup viewGroup = (ViewGroup)view;

				int childCount = viewGroup.getChildCount();
				for(int index = 0; index < childCount; index++){
					View child = viewGroup.getChildAt(index);
					nullViewDrawablesRecursive(child);
				}
			}catch(Exception e){}
			nullViewDrawable(view);
		}    
	}
	
	static void closeQuietly(Closeable closeable) 
	{
		if (closeable == null)
			return;
		try {
			closeable.close();
		} catch (Throwable t) {
		}
	}

	static void closeQuietly(HttpURLConnection httpUrlConnection) 
	{
		if (httpUrlConnection == null)
			return;
		try {
			httpUrlConnection.disconnect();
		} catch (Throwable t) {
			//logger.warn("Unable to disconnect " + httpUrlConnection + ": ", t);
		}
	}
	
	static String executePost(String url, String parameters, String userAgent) throws IOException
	{
		HttpURLConnection httpUrlConnection = null;
		OutputStream outputStream = null;
		InputStream inputStream = null;
		try {
			httpUrlConnection = (HttpURLConnection) new URL(url).openConnection();
			httpUrlConnection.setConnectTimeout(7500);
			httpUrlConnection.setReadTimeout(7500);
			httpUrlConnection.setRequestProperty("User-Agent", userAgent);
			httpUrlConnection.setRequestMethod("POST");
			httpUrlConnection.setDoOutput(true);
			httpUrlConnection.connect();
			outputStream = httpUrlConnection.getOutputStream();
			outputStream.write(parameters.getBytes("UTF-8"));

			try {
				inputStream = httpUrlConnection.getInputStream();
			} catch (IOException e) {
				//andrologger.warn("An error occurred while POSTing to " + url, e);
			}

			int code = httpUrlConnection.getResponseCode();
			if(code == 200) return fromInputStream(inputStream);
		} finally {
			closeQuietly(outputStream);
			closeQuietly(httpUrlConnection);
		}
		return null;
	}
	
	static String fromInputStream(InputStream inputStream) throws IOException
	{
		if (inputStream == null)
			return null;

		BufferedReader reader = null;

		try {
			reader =
					new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
			StringBuilder response = new StringBuilder();

			String line = null;
			while ((line = reader.readLine()) != null)
				response.append(line);

			return response.toString();
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (Throwable t) {
					// Really nothing we can do but log the error
				}
		}
	}
}
