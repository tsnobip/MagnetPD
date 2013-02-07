/*
 * Created by Paul Tsnobiladz� and Johan Delouche with the participation of Fran�ois Parra
 */

package fr.ismin.magnedpd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.puredata.android.service.PdPreferences;
import org.puredata.android.service.PdService;
import org.puredata.core.PdBase;
import org.puredata.core.PdReceiver;
import org.puredata.core.utils.IoUtils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import fr.ismin.magnetpd.R;

public class MainActivity extends Activity implements SensorEventListener, OnEditorActionListener,SharedPreferences.OnSharedPreferenceChangeListener {

	SensorManager sensorManager;

	Sensor EMCaptor;

	private float xMagnetic = 0;
	private float yMagnetic = 0;
	private float zMagnetic = 0;
	private double magneticStrength = 0;

	private TextView magnStrengthTextView;
	private TextView xMagnTextView;
	private TextView yMagnTextView;
	private TextView zMagnTextView;
	
	private EditText msg;

	private TextView logs;
	
	private static final String TAG = "Theremin Test";
	
	private PdService pdService = null;
	
	private Toast toast = null;
	
	private PdReceiver receiver = new PdReceiver() {

		private void pdPost(String msg) {
			toast("Pure Data says, \"" + msg + "\"");
		}

		@Override
		public void print(String s) {
			post(s);
		}

		@Override
		public void receiveBang(String source) {
			pdPost("bang");
		}

		@Override
		public void receiveFloat(String source, float x) {
			pdPost("float: " + x);
		}

		@Override
		public void receiveList(String source, Object... args) {
			pdPost("list: " + Arrays.toString(args));
		}

		@Override
		public void receiveMessage(String source, String symbol, Object... args) {
			pdPost("message: " + Arrays.toString(args));
		}

		@Override
		public void receiveSymbol(String source, String symbol) {
			pdPost("symbol: " + symbol);
		}
	};
	
	private final ServiceConnection pdConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			pdService = ((PdService.PdBinder)service).getService();
			initPd();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// this method will never be called
		}
	};




	/************************************************************************/
	/** Manage life cycle ******************************************************/
	/***********************************************************************/
	/** Appelé à la création de l’activité. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		magnStrengthTextView = ((TextView) findViewById(R.id.MagnStrength));
		xMagnTextView = ((TextView) findViewById(R.id.xMagn));
		yMagnTextView = ((TextView) findViewById(R.id.yMagn));
		zMagnTextView = ((TextView) findViewById(R.id.zMagn));
		logs = ((TextView) findViewById(R.id.logs));
		logs.setMovementMethod(new ScrollingMovementMethod());
		msg = (EditText) findViewById(R.id.msg_box);
		msg.setOnEditorActionListener(this);
		// Gérer les capteurs :
		// Instancier le gestionnaire des capteurs, le SensorManager
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		// Instancier l’accéléromètre
		EMCaptor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		// PureData
		
		PdPreferences.initPreferences(getApplicationContext());
		PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);
		bindService(new Intent(this, PdService.class), pdConnection, BIND_AUTO_CREATE);
		PdBase.sendFloat("vol",0.5f);
		post("test logs");
		toast("test toast");
	}
	/* * (non-Javadoc) *
	 * @see android.app.Activity#onPause() */
	@Override
	protected void onPause() {
		// unregister the sensor (désenregistrer le capteur)
		sensorManager.unregisterListener(this, EMCaptor);
		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		/* Ce qu’en dit Google dans le cas de l’accéléromètre :
		 * «  Ce n’est pas nécessaire d’avoir les évènements des capteurs à un rythme trop rapide.
		 * En utilisant un rythme moins rapide (SENSOR_DELAY_UI), nous obtenons un filtre
		 * automatique de bas-niveau qui "extrait" la gravité  de l’accélération.
		 * Un autre bénéfice étant que l’on utilise moins d’énergie et de CPU. »
		 */
		sensorManager.registerListener(this, EMCaptor, SensorManager.SENSOR_DELAY_UI);
		super.onResume();
	}
	/********************************************************************/
	/** SensorEventListener*************************************************/
	/********************************************************************/
	/*
	 * (non-Javadoc)
	 *
	 * @see android.hardware.SensorEventListener#onAccuracyChanged(android.hardware.Sensor, int)
	 */
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Rien à faire la plupart du temps
	}

	public void onSensorChanged(SensorEvent event) {
		// Lire les données quand elles correspondent à notre capteur:
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			// Valeur du vecteur du champ magnétique (x,y,z)
			xMagnetic = event.values[0];
			yMagnetic = event.values[1];
			zMagnetic = event.values[2];
			// Valeur de la norme de ce vecteur
			magneticStrength=Math.sqrt((double)
					(xMagnetic*xMagnetic+
							yMagnetic*yMagnetic+
							zMagnetic*zMagnetic));
			// faire quelque chose, demander à mettre à jour l’IHM, par exemple :
			redraw();
			PdBase.sendFloat("freq",(float) (magneticStrength));
		}
	}

	public void redraw(){
		magnStrengthTextView.setText("Magnetic Strength: "+ magneticStrength);
		xMagnTextView.setText("x : "+xMagnetic);
		yMagnTextView.setText("y : "+yMagnetic);
		zMagnTextView.setText("z : "+zMagnetic);


	}
	
	public void playTheremin(){

	}
	

	
	private void initPd() {
		Resources res = getResources();
		File patchFile = null;
		try {
			PdBase.setReceiver(receiver);
			PdBase.subscribe("android");
			InputStream in = res.openRawResource(R.raw.theremin);
			patchFile = IoUtils.extractResource(in, "theremin.pd", getCacheDir());
			PdBase.openPatch(patchFile);
			startAudio();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			finish();
		} finally {
			if (patchFile != null) patchFile.delete();
		}
	}

	private void startAudio() {
		String name = getResources().getString(R.string.app_name);
		try {
			pdService.initAudio(-1, -1, -1, -1);   // negative values will be replaced with defaults/preferences
			pdService.startAudio(new Intent(this, MainActivity.class), R.drawable.icon, name, "Return to " + name + ".");
		} catch (IOException e) {
			toast(e.toString());
		}
	}

	private void cleanup() {
		try {
			unbindService(pdConnection);
		} catch (IllegalArgumentException e) {
			// already unbound
			pdService = null;
		}
	}

	private void toast(final String msg) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (toast == null) {
					toast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT);
				}
				toast.setText(TAG + ": " + msg);
				toast.show();
			}
		});
	}
	
	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		evaluateMessage(msg.getText().toString());
		return true;
	}

	private void evaluateMessage(String s) {
		String dest = "test", symbol = null;
		boolean isAny = s.length() > 0 && s.charAt(0) == ';';
		Scanner sc = new Scanner(isAny ? s.substring(1) : s);
		if (isAny) {
			if (sc.hasNext()) dest = sc.next();
			else {
				toast("Message not sent (empty recipient)");
				return;
			}
			if (sc.hasNext()) symbol = sc.next();
			else {
				toast("Message not sent (empty symbol)");
			}
		}
		List<Object> list = new ArrayList<Object>();
		while (sc.hasNext()) {
			if (sc.hasNextInt()) {
				list.add(new Float(sc.nextInt()));
			} else if (sc.hasNextFloat()) {
				list.add(sc.nextFloat());
			} else {
				list.add(sc.next());
			}
		}
		if (isAny) {
			PdBase.sendMessage(dest, symbol, list.toArray());
		} else {
			switch (list.size()) {
			case 0:
				PdBase.sendBang(dest);
				break;
			case 1:
				Object x = list.get(0);
				if (x instanceof String) {
					PdBase.sendSymbol(dest, (String) x);
				} else {
					PdBase.sendFloat(dest, (Float) x);
				}
				break;
			default:
				PdBase.sendList(dest, list.toArray());
				break;
			}
		}
	}
	
	private void post(final String s) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				logs.append(s + ((s.endsWith("\n")) ? "" : "\n"));
			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		cleanup();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		startAudio();
	}


}
