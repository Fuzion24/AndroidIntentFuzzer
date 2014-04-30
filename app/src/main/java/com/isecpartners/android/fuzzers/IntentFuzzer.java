package com.isecpartners.android.fuzzers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

public class IntentFuzzer extends Activity {
	protected Spinner mIntentSpin = null;
	protected Spinner mTypeSpin = null;
	protected EditText mInput = null;
	protected EditText mComp = null;
	protected TextView mComponentsLabel = null;
	protected Button mAdd = null;
	protected Button mFuzzSingle = null;
	protected Button mFuzzAll = null;
    protected CheckBox mSystemAppsOnly = null;
	protected TextView mOut = null;
	protected ArrayList<String> mComponentNames = new ArrayList<String>();
	protected ArrayList<String> mTypes = new ArrayList<String>();

	// the currently selected IPC type, specified as a string.
	public String mCurrentType = null;
	// list of ComponentNames for the current IPC type
	public ArrayList<ComponentName> mKnownComponents = new ArrayList<ComponentName>();


    private static final String TAG = "Null Intent Fuzzer";

	/**
	 * Mapping from ipcTypes to Strings for display. Overhead because you can't
	 * switch on strings.
	 */

	public enum IPCType {
		ACTIVITIES, BROADCASTS, PROVIDERS, SERVICES, INSTRUMENTATIONS
	}

	protected static Map<IPCType, String> ipcTypesToNames = new TreeMap<IPCType, String>();
	protected static Map<String, IPCType> ipcNamesToTypes = new HashMap<String, IPCType>();
	static {
		ipcTypesToNames.put(IPCType.ACTIVITIES, "Activities");
		ipcTypesToNames.put(IPCType.BROADCASTS, "Broadcasts");
		ipcTypesToNames.put(IPCType.PROVIDERS, "Providers");
		ipcTypesToNames.put(IPCType.SERVICES, "Services");
		ipcTypesToNames.put(IPCType.INSTRUMENTATIONS, "Instrumentations");

		for (Entry<IPCType, String> e : ipcTypesToNames.entrySet()) {
			ipcNamesToTypes.put(e.getValue(), e.getKey());
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		initControls();
        setKnownComponents(getExportedComponents(IPCType.BROADCASTS, false));
	}

	/**
	 * Takes a list of component names, and uses it to populate the
	 * mComponentNames list. This list is used to render a human readable list
	 * of components by Class name.
	 * 
	 * @param newComponents
	 */
	public void setKnownComponents(ArrayList<ComponentName> newComponents) {
		int diff = 0;
		mKnownComponents.clear();
		mKnownComponents.addAll(newComponents);
		mComponentNames.clear();
		for (ComponentName n : mKnownComponents) {
			if (mComponentNames.contains(n.getClassName()))
				diff++;
			else
				mComponentNames.add(n.getClassName());
		}

		if (diff != 0)
			Toast.makeText(this, diff + " component name collision(s)",
					Toast.LENGTH_SHORT).show();
	}

	/**
	 * For any type, provide the registered instances based on what the package
	 * manager has on file. Only provide exported components.
	 * 
	 * @param type
	 *            IPC requested, activity, broadcast, etc.
	 * @return
	 */
	protected ArrayList<ComponentName> getExportedComponents(IPCType type, Boolean systemAppsOnly) {
		ArrayList<ComponentName> found = new ArrayList<ComponentName>();
		PackageManager pm = getPackageManager();
		for (PackageInfo pi : pm
				.getInstalledPackages(PackageManager.GET_DISABLED_COMPONENTS
						| PackageManager.GET_ACTIVITIES
						| PackageManager.GET_RECEIVERS
						| PackageManager.GET_INSTRUMENTATION
						| PackageManager.GET_PROVIDERS
						| PackageManager.GET_SERVICES)) {
			PackageItemInfo items[] = null;

            if( systemAppsOnly && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0){
                Log.d(TAG, "Not system app" + pi.packageName);
                continue;
            }else {
                Log.d(TAG, "System app: " + pi.packageName);
            }

			switch (type) {
			case ACTIVITIES:
				items = pi.activities;
				break;
			case BROADCASTS:
				items = pi.receivers;
				break;
			case SERVICES:
				items = pi.services;
				break;
			case PROVIDERS:
				items = pi.providers;
				break;
			case INSTRUMENTATIONS:
				items = pi.instrumentation;
			}

			if (items != null)
				for (PackageItemInfo pii : items){
                    found.add(new ComponentName(pi.packageName, pii.name));
                }
		}

		return found;
	}

	protected void defineControls() {
		mIntentSpin = (Spinner) this.findViewById(R.id.intentSelect);
		mTypeSpin = (Spinner) this.findViewById(R.id.typeSelect);
		mComponentsLabel = (TextView) this.findViewById(R.id.actionLabel);
		mAdd = (Button) this.findViewById(R.id.addIntent);
		mFuzzSingle = (Button) this.findViewById(R.id.fuzzSingle);
		mFuzzAll = (Button) this.findViewById(R.id.fuzzAll);
		mOut = (TextView) this.findViewById(R.id.output);
		mInput = (EditText) this.findViewById(R.id.input);
		mComp = (EditText) this.findViewById(R.id.comp);
        mSystemAppsOnly = (CheckBox) this.findViewById(R.id.onlySystemApps);
	}

	protected void initControls() {
		defineControls();

		ArrayAdapter<String> actionAA = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_dropdown_item, mComponentNames);

		for (String name : ipcTypesToNames.values())
			mTypes.add(name);
		mCurrentType = mTypes.get(0);
		updateType();

		ArrayAdapter<String> typeAA = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_dropdown_item, mTypes);

		mAdd.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				runClicked();
			}
		});

        mSystemAppsOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean systemAppsOnly) {
                updateComponents();
            }
        });
		mIntentSpin.setAdapter(actionAA);
		mFuzzSingle.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mOut.append(fuzzNullSingle(ipcNamesToTypes.get(mCurrentType)));
			}
		});

		mFuzzAll.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mOut.append(fuzzAll(ipcNamesToTypes.get(mCurrentType)));
			}
		});

		mTypeSpin.setAdapter(typeAA);
		mTypeSpin.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> a, View v, int i, long l) {
				updateType();
			}

			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
	}

	/**
	 * Handles the clicking of the "Fuzz all" GUI button, passed the type of IPC
	 * to fuzz. The type must be matched to the currently populated known
	 * components as this code is tightly coupled to the UIs implementation.
	 * 
	 * @param t
	 *            type if IPC
	 * @return String that gives a summary of what was done
	 */
	public String fuzzAll(IPCType t) {
		switch (t) {
		case ACTIVITIES:
			return "Activities not yet implemented";
		case BROADCASTS:
			int count = fuzzAllBroadcasts(mKnownComponents);
			return "Sent: " + count + " broadcasts";
		case SERVICES:
			count = fuzzAllServices(mKnownComponents);
			return "Started: " + count + " broadcasts";
		default:
			return "Not Implemented";
		}
	}

	public String fuzzNullSingle(IPCType t) {
		ComponentName toTest = null;
		Intent i = new Intent();
		String className = mIntentSpin.getSelectedItem().toString();
		for (ComponentName c : mKnownComponents) {
			if (c.getClassName().equals(className)) {
				toTest = c;
				break;
			}
		}
		i.setComponent(toTest);

		if (sendIntentByType(i, t)) {
			return "Sent: " + i;
		} else {
			return "Send failed. ";
		}
	}

	protected int fuzzAllBroadcasts(List<ComponentName> comps) {
		int count = 0;
		for (int i = 0; i < comps.size(); i++) {
			Intent in = new Intent();
			in.setComponent(comps.get(i));
			sendBroadcast(in);
			count++;
		}
		return count;
	}

	protected int fuzzAllServices(List<ComponentName> comps) {
		int count = 0;
		for (int i = 0; i < comps.size(); i++) {
			Intent in = new Intent();
			in.setComponent(comps.get(i));
			try {
				startService(in);
			} catch (Exception e) {
				mOut.append("Can't launch " + comps.get(i) + " "
						+ e.getMessage() + "\n");
			}
			count++;
		}
		return count;
	}

	protected boolean sendIntentByType(Intent i, IPCType t) {
		try {
			switch (t) {
			case ACTIVITIES:
				startActivity(i);
				return true;
			case BROADCASTS:
				sendBroadcast(i);
				return true;
			case SERVICES:
				startService(i); // stopping these might be nice too
				return true;
			case PROVIDERS:
				// uh - providers don't use Intents...what am I doing...
				Toast.makeText(this,
						"Proivders don't use Intents, ignore this setting.",
						Toast.LENGTH_SHORT).show();
				return false;
			case INSTRUMENTATIONS:
				Toast
						.makeText(
								this,
								"Instrumentations aren't Intent based... starting Instrumentation.",
								Toast.LENGTH_SHORT).show();
				startInstrumentation(i.getComponent(), null, null); // not
				// intent based you could fuzz these params, if anyone cared.
				return true;
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	protected Intent fuzzBroadcast(ComponentName toTest) {
		Intent i = new Intent();
		i.setComponent(toTest);
		sendBroadcast(i);
		return i;
	}

	protected void fuzzActivity() {

	}

	protected void updateType() {
		Object sel = mTypeSpin.getSelectedItem();
		if (sel != null) {
			mCurrentType = mTypeSpin.getSelectedItem().toString();
			updateComponents();
			mComponentsLabel.setText("Componets ("
					+ Integer.toString(mKnownComponents.size()) + " ):");
		}
	}

	protected void updateComponents() {
		IPCType cur = ipcNamesToTypes.get(mCurrentType);
        Boolean systemAppsOnly =  mSystemAppsOnly != null && mSystemAppsOnly.isChecked();
		setKnownComponents(getExportedComponents(cur, systemAppsOnly));
		ArrayAdapter<String> actionAA = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_dropdown_item, mComponentNames);
		mIntentSpin.setAdapter(actionAA);

		// do something to build the list of actions
	}

	protected void runClicked() {
		mOut.append("Button Click Not Implemented\n");
		// mKnownComponents.add(mInput.getText().toString());
	}
}