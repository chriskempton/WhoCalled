package com.mynameistodd.whocalled;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.Tracker;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MissedCallsList extends ListActivity {

	private Context curContext;
	public String number;
	String result = "";
	ProgressDialog progressDialog;
	private Util whoCalledUtil;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        EasyTracker.getInstance().activityStart(this);
        Tracker tracker = EasyTracker.getTracker();

        setContentView(R.layout.list_view);
		
		curContext = this;
		whoCalledUtil = new Util(curContext);
		
		Intent intent = getIntent();
		int callType = intent.getIntExtra("com.mynameistodd.whocalled.calllist", 0);
		
		String selection = CallLog.Calls.TYPE + '=';
		switch (callType) {
		default:
		case 1:
			selection += CallLog.Calls.MISSED_TYPE;
			tracker.sendView("/callList/view/missed");
			break;
		case 2:
			selection += CallLog.Calls.OUTGOING_TYPE;
			tracker.sendView("/callList/view/outgoing");
			break;
		case 3:
			selection += CallLog.Calls.INCOMING_TYPE;
			tracker.sendView("/callList/view/incoming");
			break;
		}
		
		selection += " and " + CallLog.Calls.CACHED_NAME + " IS NULL";
		
		Cursor c = managedQuery(CallLog.Calls.CONTENT_URI, null,selection,null,CallLog.Calls.DEFAULT_SORT_ORDER);
		
		SimpleCursorAdapter sca = new SimpleCursorAdapter(this, R.layout.list_view_items, c, new String[] { CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.NUMBER }, new int[] { R.id.displayNumber, R.id.displayDate, R.id.displayName })
		{
			@Override
			public void setViewText(TextView v, String text) {
				super.setViewText(v, text);
				if (v.getId() == R.id.displayNumber)
				{
					v.setText(PhoneNumberUtils.formatNumber(text));
				}
				else if (v.getId() == R.id.displayName)
				{
					String fromCache = whoCalledUtil.readFromCache(text);
					v.setText(fromCache);
				}
				else
				{
					v.setText(DateUtils.formatDateTime(curContext, Long.parseLong(text), DateUtils.FORMAT_NUMERIC_DATE));
				}
			}
		};

		setListAdapter(sca);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		
		RelativeLayout relativeMaster = (RelativeLayout)v;
		TextView tv = (TextView)relativeMaster.getChildAt(2);
		
		number = (String) tv.getText();
		number = number.replace("-", "");
		
		progressDialog = new ProgressDialog(this);
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(true);
		progressDialog.setMessage("Loading...");
		progressDialog.show();
        
        registerForContextMenu(getListView());
        Thread myThread = new Thread(new Runnable() {
            public void run() {
            	Looper.prepare();
				result = whoCalledUtil.getWhoCalledResponse(number);
				showDialog(result);
                progressDialog.dismiss();
                Looper.loop();	
            }
        });
        myThread.start();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.item_long_click, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
	  //AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	  switch (item.getItemId()) {
	  case R.id.item1:
		  Uri telNum = Uri.parse("tel:" + number);
		  startActivity(new Intent(Intent.ACTION_DIAL, telNum));
	    return true;
	  default:
	    return super.onContextItemSelected(item);
	  }
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		EasyTracker.getInstance().setContext(this);
		Tracker tracker = EasyTracker.getTracker();

		if (requestCode == 1 && resultCode == RESULT_OK)
		{
			Toast.makeText(curContext, "Submitted!", Toast.LENGTH_SHORT).show();
			tracker.sendView("/callList/submited/"+number);
		}
		else
		{
			String response = "Error: ";
			if (data != null)
			{
				response += data.getStringExtra("com.mynameistodd.whocalled.response");
			}
			Toast.makeText(curContext, response, Toast.LENGTH_LONG).show();
			tracker.sendView("/callList/error/"+number);
		}
	}
	
	public void showDialog(String who)
	{
		EasyTracker.getInstance().setContext(this);
        EasyTracker.getTracker().sendView("/callList/click/"+number);

		AlertDialog.Builder dialog = new AlertDialog.Builder(curContext);
		dialog.setTitle("Most Popular Response");
		dialog.setMessage(who);
		dialog.setPositiveButton(R.string.submit_your_response,
				new DialogInterface.OnClickListener() {
			
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(curContext, SubmitResponse.class);
						intent.putExtra("com.mynameistodd.whocalled.unknownNumber", number);
						startActivityForResult(intent, 1);
					}
				});
		
		dialog.show();
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
}
