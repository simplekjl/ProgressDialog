package com.example.progressdialog;

import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

public class MainActivity extends Activity implements MainFragment.Callbacks
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	@Override
	public void onTaskFinished()
	{
		// Hooray. A toast to our success.
		Toast.makeText(this, "Task finished!", Toast.LENGTH_LONG).show();
		// NB: I'm going to blow your mind again: the "int duration" parameter of makeText *isn't*
		// the duration in milliseconds. ANDROID Y U NO ENUM? 
	}
}
