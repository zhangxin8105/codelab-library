package codelab.library.network;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import codelab.library.global.GlobalApplication;

/**
 * 다양한 곳에서 다운로드 하기 위한 라이브러리
 * @author hovan
 *
 */
public class Downloader {
	
	/**
	 * 해당 URL의 파일을 해당 위치에 다운로드 하는 함수<br/>
	 * 제대로 저장이 되지 않은 경우 null을 리턴한다.
	 * @param url
	 * @param filePath
	 * @return
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public static boolean fromURL(String url, String filePath) throws MalformedURLException, IOException {
		return fromURL(url, filePath, null);
	}
	
	public static boolean fromURL(String url, String filePath, ProgressListener progress) throws MalformedURLException, IOException {
		InputStream inputStream = null;
		FileOutputStream fileOutputStream = null;
		byte[] buf = new byte[100];
		AndroidHttpClient httpClient = null;
		try {
			Context context = GlobalApplication.getContext();
			httpClient = AndroidHttpClient.newInstance(context.getPackageName(), context);
			HttpGet pageGet = new HttpGet(URI.create(url));
			HttpResponse response = httpClient.execute(pageGet);
			inputStream = response.getEntity().getContent();
			long lenghtOfFile = response.getEntity().getContentLength();

			fileOutputStream = new FileOutputStream(filePath);
			
			/** URL Connection 방식을 안드로이드에서는 권하고 있지만 Get방식으로 Content등을 읽어올 때 문제가 발생한다.*/
//			HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
//		    urlConnection.setRequestMethod("GET");
//		    urlConnection.setDoOutput(true);
//		    urlConnection.connect();
//			inputStream = urlConnection.getInputStream();
//			int lenghtOfFile = urlConnection.getContentLength();
			int count = 0;
			long total = 0;
			while ((count = inputStream.read(buf)) != -1) {
				total += count;
				fileOutputStream.write(buf, 0, count);
				fileOutputStream.flush();
				if(progress != null) {
					progress.onPercent((int)(total*100/lenghtOfFile));
				}
			}
			httpClient.close();
		} finally {
			try {
				if (inputStream != null)
					inputStream.close();
				if (fileOutputStream != null)
					fileOutputStream.close();
				if(httpClient != null) {
					httpClient.close();
				}
			} catch (IOException ie) {}
		}
		
		File file = new File(filePath);
		if(file.exists()) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * 백분위로 percent를 callback 해주기 위한 interface
	 * @author hovan
	 *
	 */
	public interface ProgressListener {
		void onPercent(int percent);
	}
}
