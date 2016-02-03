package com.example.progressdialog;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ProgressBar;

public class MainFragment extends Fragment implements OnClickListener
{
	// This code up to onDetach() is all to get easy callbacks to the Activity.	
	private Callbacks mCallbacks = sDummyCallbacks;

	public interface Callbacks
	{
		public void onTaskFinished();
	}
	private static Callbacks sDummyCallbacks = new Callbacks()
	{
		public void onTaskFinished() { }
	};

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		if (!(activity instanceof Callbacks))
		{
			throw new IllegalStateException("Activity must implement fragment's callbacks.");
		}
		mCallbacks = (Callbacks) activity;
	}

	@Override
	public void onDetach()
	{
		super.onDetach();
		mCallbacks = sDummyCallbacks;
	}

	// Save a reference to the fragment manager. This is initialised in onCreate().
	private FragmentManager mFM;

	// Code to identify the fragment that is calling onActivityResult(). We don't really need
	// this since we only have one fragment to deal with.
	static final int TASK_FRAGMENT = 0;

	// Tag so we can find the task fragment again, in another instance of this fragment after rotation.
	static final String TASK_FRAGMENT_TAG = "task";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// At this point the fragment may have been recreated due to a rotation,
		// and there may be a TaskFragment lying around. So see if we can find it.
		mFM = getFragmentManager();
		// Check to see if we have retained the worker fragment.
		TaskFragment taskFragment = (TaskFragment)mFM.findFragmentByTag(TASK_FRAGMENT_TAG);

		if (taskFragment != null)
		{
			// Update the target fragment so it goes to this fragment instead of the old one.
			// This will also allow the GC to reclaim the old MainFragment, which the TaskFragment
			// keeps a reference to. Note that I looked in the code and setTargetFragment() doesn't
			// use weak references. To be sure you aren't leaking, you may wish to make your own
			// setTargetFragment() which does.
			taskFragment.setTargetFragment(this, TASK_FRAGMENT);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_main, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		
		// Callback for the "start task" button. I originally used the XML onClick()
		// but it goes to the Activity instead.
		view.findViewById(R.id.taskButton).setOnClickListener(this);
	}
	
	@Override
	public void onClick(View v)
	{
		// We only have one click listener so we know it is the "Start Task" button.

		// We will create a new TaskFragment.
		TaskFragment taskFragment = new TaskFragment();
		// And create a task for it to monitor. In this implementation the taskFragment
		// executes the task, but you could change it so that it is started here.
		taskFragment.setTask(new MyTask());
		// And tell it to call onActivityResult() on this fragment.
		taskFragment.setTargetFragment(this, TASK_FRAGMENT);
		
		// Show the fragment.
		// I'm not sure which of the following two lines is best to use but this one works well.
		taskFragment.show(mFM, TASK_FRAGMENT_TAG);
//		mFM.beginTransaction().add(taskFragment, TASK_FRAGMENT_TAG).commit();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == TASK_FRAGMENT && resultCode == Activity.RESULT_OK)
		{
			// Inform the activity. 
			mCallbacks.onTaskFinished();
		}
	}

	// This and the other inner class can be in separate files if you like.
	// There's no reason they need to be inner classes other than keeping everything together.
	public static class TaskFragment extends DialogFragment
	{
		// The task we are running.
		MyTask mTask;
		ProgressBar mProgressBar;

		public void setTask(MyTask task)
		{
			mTask = task;
			
			// Tell the AsyncTask to call updateProgress() and taskFinished() on this fragment.
			mTask.setFragment(this);
		}

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			
			// Retain this instance so it isn't destroyed when MainActivity and
			// MainFragment change configuration.
			setRetainInstance(true);

			// Start the task! You could move this outside this activity if you want.
			if (mTask != null)
				mTask.execute();
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState)
		{
			View view = inflater.inflate(R.layout.fragment_task, container);
			mProgressBar = (ProgressBar)view.findViewById(R.id.progressBar);
			
			getDialog().setTitle("Progress Dialog");
			
			// If you're doing a long task, you probably don't want people to cancel
			// it just by tapping the screen!
			getDialog().setCanceledOnTouchOutside(false);

			return view;
		}
		
		// This is to work around what is apparently a bug. If you don't have it
		// here the dialog will be dismissed on rotation, so tell it not to dismiss.
		@Override
		public void onDestroyView()
		{
			if (getDialog() != null && getRetainInstance())
				getDialog().setDismissMessage(null);
			super.onDestroyView();
		}
		
		// Also when we are dismissed we need to cancel the task.
		@Override
		public void onDismiss(DialogInterface dialog)
		{
			super.onDismiss(dialog);
			// If true, the thread is interrupted immediately, which may do bad things.
			// If false, it guarantees a result is never returned (onPostExecute() isn't called)
			// but you have to repeatedly call isCancelled() in your doInBackground()
			// function to check if it should exit. For some tasks that might not be feasible.
			if (mTask != null)
				mTask.cancel(false);

			// You don't really need this if you don't want.
			if (getTargetFragment() != null)
				getTargetFragment().onActivityResult(TASK_FRAGMENT, Activity.RESULT_CANCELED, null);
		}

		@Override
		public void onResume()
		{
			super.onResume();
			// This is a little hacky, but we will see if the task has finished while we weren't
			// in this activity, and then we can dismiss ourselves.
			if (mTask == null)
				dismiss();
		}

		// This is called by the AsyncTask.
		public void updateProgress(int percent)
		{
			mProgressBar.setProgress(percent);
		}

		// This is also called by the AsyncTask.
		public void taskFinished()
		{
			// Make sure we check if it is resumed because we will crash if trying to dismiss the dialog
			// after the user has switched to another app.
			if (isResumed())
				dismiss();
			
			// If we aren't resumed, setting the task to null will allow us to dimiss ourselves in
			// onResume().
			mTask = null;
			
			// Tell the fragment that we are done.
			if (getTargetFragment() != null)
				getTargetFragment().onActivityResult(TASK_FRAGMENT, Activity.RESULT_OK, null);
		}
	}

	// This is a fairly standard AsyncTask that does some dummy work.
	public static class MyTask extends AsyncTask<Void, Void, Void>
	{
		TaskFragment mFragment;
		int mProgress = 0;

		void setFragment(TaskFragment fragment)
		{
			mFragment = fragment;
		}

		@Override
		protected Void doInBackground(Void... params)
		{
			// Do some longish task. This should be a task that we don't really
			// care about continuing
			// if the user exits the app.
			// Examples of these things:
			// * Logging in to an app.
			// * Downloading something for the user to view.
			// * Calculating something for the user to view.
			// Examples of where you should probably use a service instead:
			// * Downloading files for the user to save (like the browser does).
			// * Sending messages to people.
			// * Uploading data to a server.
			for (int i = 0; i < 10; i++)
			{
				// Check if this has been cancelled, e.g. when the dialog is dismissed.
				if (isCancelled())
					return null;
				
				SystemClock.sleep(2000);
				mProgress = i * 10;
				publishProgress();
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Void... unused)
		{
			if (mFragment == null)
				return;
			mFragment.updateProgress(mProgress);
		}

		@Override
		protected void onPostExecute(Void unused)
		{
			if (mFragment == null)
				return;
			mFragment.taskFinished();
		}
	}
}
