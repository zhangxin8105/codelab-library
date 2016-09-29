package com.hovans.network;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.android.volley.*;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * NetHttpTask.java
 *
 * @author Hovan Yoo
 */
public class NetHttpTask {

	static final String TAG = NetHttpTask.class.getSimpleName();

	static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'ZZZ").create();

	static final int REQUEST_TIMEOUT = 10, RESPONSE_OK = 200, TIMEOUT = 10000;

	@Expose
	final String url;
	final String waitString;
	@Expose
	final HashMap<String, String> params;

	static RequestQueue queue;

	//	final Context context;
	final boolean synchronousMode;
	final Activity activityForProgress;
	//	final SSLSocketFactory sslSocketFactory;
	ProgressDialog progressDialog;
	StringResponseHandler callbackString;
	ResponseHandler callbackObject;
	Handler handler;

	Class type;

	public <T> void post(Class<T> classOfT, final ResponseHandler<T> callback) {
		type = classOfT;
		this.callbackObject = callback;

		post(null);
	}

	public void post(final StringResponseHandler callback) {
		this.callbackString = callback;

		if(activityForProgress != null) {
			activityForProgress.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					progressDialog = new ProgressDialog(activityForProgress);
					progressDialog.setMessage(waitString);
					progressDialog.setCancelable(false);
					progressDialog.show();
				}
			});
		}

		if(synchronousMode == false && Looper.myLooper() != null) {
			StringRequest stringRequest = new StringRequest(StringRequest.Method.POST, url, stringListener, errorListener) {
				@Override
				protected Map<String, String> getParams() throws AuthFailureError {
					return params;
				}
			};
			stringRequest.setRetryPolicy(new DefaultRetryPolicy(
					TIMEOUT,
					DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
					DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
			queue.add(stringRequest);
			handler = new Handler();
		} else {
			RequestFuture<String> future = RequestFuture.newFuture();
			StringRequest request = new StringRequest(StringRequest.Method.POST, url, future, errorListener) {
				@Override
				protected Map<String, String> getParams() throws AuthFailureError {
					return params;
				}
			};
			request.setRetryPolicy(new DefaultRetryPolicy(
					TIMEOUT,
					DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
					DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
			queue.add(request);
			try {
				String result = future.get(REQUEST_TIMEOUT, TimeUnit.SECONDS);
				stringListener.onResponse(result);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				Log.w(TAG, e);
				errorListener.onErrorResponse(new VolleyError(e));
			}
		}
	}

	Response.Listener<String> stringListener = new Response.Listener<String>() {
		@Override
		public void onResponse(final String response) {
			if (handler != null) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						handleResponse(RESPONSE_OK, response, null);
					}
				});
			} else {
				handleResponse(RESPONSE_OK, response, null);
			}
		}
	};

	Response.ErrorListener errorListener = new Response.ErrorListener() {
		@Override
		public void onErrorResponse(VolleyError error) {
			int statusCode = -1;
			if (error.networkResponse != null) {
				statusCode = error.networkResponse.statusCode;
			}
			handleResponse(statusCode, null, error.getCause());
		}
	};

	public String makeBackup() {
		return gson.toJson(this);
	}

	public static NetHttpTask restoreBackup(String gsonData) {
		NetHttpTask backup = gson.fromJson(gsonData, NetHttpTask.class);
		NetHttpTask task = new NetHttpTask(backup);
		return task;
	}

	void handleResponse(int statusCode, String responseString, Throwable e) {
		closeDialogIfItNeeds();
		switch(statusCode) {
			case RESPONSE_OK:
				try {
					JSONObject jsonObject = new JSONObject(responseString);

					if(jsonObject.has("code") && jsonObject.getInt("code") != 0) {
						handleFailResponse(statusCode, gson.fromJson(responseString, NetHttpResult.class), e);
					} else {
						String resultString;
						if(jsonObject.has("result")) {
							resultString = jsonObject.getString("result");
						} else {
							resultString = responseString;
						}

						handleSuccessResponse(statusCode, resultString);
					}
				} catch (Exception ex) {
					handleFailResponse(statusCode, null, ex);
				}

				break;
			default:
				try {
					handleFailResponse(statusCode, gson.fromJson(responseString, NetHttpResult.class), e);
				} catch (Exception ex) {
					handleFailResponse(statusCode, null, ex);
				}
				break;
		}
	}

	void handleSuccessResponse(int statusCode, String resultString) {
		if(callbackString != null) {
			callbackString.onSuccess(statusCode, resultString);
		} else if(callbackObject != null) {
			Object resultObject = gson.fromJson(resultString, type);
			callbackObject.onSuccess(statusCode, resultObject, resultString);
		}
	}

	void handleFailResponse(int statusCode, NetHttpResult result, Throwable e) {
		if(callbackString != null) {
			callbackString.onFail(statusCode, result, e);
		} else if(callbackObject != null) {
			callbackObject.onFail(statusCode, result, e);
		}
	}

	void closeDialogIfItNeeds() {
		if(progressDialog != null && progressDialog.isShowing()) {
			activityForProgress.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					try {
						progressDialog.dismiss();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
	}

	public interface StringResponseHandler {
		void onSuccess(int statusCode, String result);
		void onFail(int statusCode, NetHttpResult result, Throwable e);
	}

	public interface ResponseHandler<T> {
		void onSuccess(int statusCode, T result, String resultString);
		void onFail(int statusCode, NetHttpResult result, Throwable e);
	}

	public NetHttpTask(NetHttpTask backup) {
		waitString = backup.waitString;
		url = backup.url;
		params = backup.params;
//		sslSocketFactory = backup.sslSocketFactory;
		synchronousMode = true;
		activityForProgress = null;
	}

	private NetHttpTask(Context context, String url, HashMap<String, String> params, boolean syncronous, Activity activityForProgress, String waitString, SSLSocketFactory sslSocketFactory) {
		this.waitString = waitString;
		this.url = url;
		this.params = params;
//		this.sslSocketFactory = sslSocketFactory;
		if (queue == null) {
			queue = Volley.newRequestQueue(context);
			queue.start();
			if (sslSocketFactory != null) HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
		}

		if(Looper.myLooper() == null) synchronousMode = true;
		else {
			this.synchronousMode = syncronous;
		}
		this.activityForProgress = activityForProgress;
	}

	public static class Builder {
		String url;
		HashMap<String, String> params = new HashMap<>();
		boolean synchronousMode;

		Context context;

		SSLSocketFactory sslSocketFactory;
		Activity activityForProgress;
		String waitString;

		public Builder(Context context) {
			this.context = context;
		}

//		final String URL_BASE = "http://autoguard.hovans.com";

		public Builder setParams(HashMap<String, String> params) {
			this.params = params;
			return this;
		}

		public Builder addParam(String key, Object value) {
			params.put(key, String.valueOf(value));
			return this;
		}

		public Builder setUrl(String url) {
			this.url = url;
			return this;
		}

		public Builder showProgress(Activity activity, String waitString) {
			activityForProgress = activity;
			this.waitString = waitString;
			return this;
		}

		public Builder setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
			this.sslSocketFactory = sslSocketFactory;
			return this;
		}

		public Builder setSyncMode(boolean synchronousMode) {
			this.synchronousMode = synchronousMode;
			return this;
		}

		public NetHttpTask build() {
			return new NetHttpTask(context, url, params, synchronousMode, activityForProgress, waitString, sslSocketFactory);
		}
	}
}