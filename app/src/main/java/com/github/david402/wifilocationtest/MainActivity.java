package com.github.david402.wifilocationtest;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.conn.ssl.SSLSocketFactory;

public class MainActivity extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    private WebView mWebView;
    private AsyncHttpClient mHttpClient;
    private WebViewClient mWebViewClient;
    private String mDefaultMapUrlStr;
    private List<ScanResult> mWifiScanResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        mDefaultMapUrlStr = "http://maps.google.com/?ll=39.774769,-74.86084";
        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebViewClient = new WebViewClient() {
            /**
             * Notify the host application that a page has finished loading. This method
             * is called only for main frame. When onPageFinished() is called, the
             * rendering picture may not be updated yet. To get the notification for the
             * new Picture, use {@link WebView.PictureListener#onNewPicture}.
             *
             * @param view The WebView that is initiating the callback.
             * @param url  The url of the page.
             */
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // hide progress when new page is loaded
                if (!mDefaultMapUrlStr.equals(url)) {
                    findViewById(R.id.progressBar).setVisibility(View.GONE);
                }
            }


        };
        mWebView.setWebViewClient(mWebViewClient);
        mHttpClient = new AsyncHttpClient();
        mHttpClient.setSSLSocketFactory(
                new SSLSocketFactory(getSslContext(),
                        SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER));

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position))
                .commit();
    }

    public void onSectionAttached(int number) {
        if (null == mWifiScanResults) {
            mTitle = "searching";
            return;
        } else {
            locateBSSID(mWifiScanResults.get(number));
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    private void updateActionBar(String title) {
        mTitle = title;
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(title);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.refresh:
                refreshWifiList();
                return super.onOptionsItemSelected(item);

            case R.id.action_settings:
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Dispatch onResume() to fragments.  Note that for better inter-operation
     * with older versions of the platform, at the point of this call the
     * fragments attached to the activity are <em>not</em> resumed.  This means
     * that in some cases the previous state may still be saved, not allowing
     * fragment transactions that modify the state.  To correctly interact
     * with fragments in their proper state, you should instead override
     * {@link #onResumeFragments()}.
     */
    @Override
    protected void onResume() {
        super.onResume();
        mWebView.loadUrl(mDefaultMapUrlStr);

        findViewById(R.id.webview).setVisibility(View.VISIBLE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        refreshWifiList();
        ScanResult bestResult = null;
        if (!mWifiScanResults.isEmpty()) {
            // fining best match!
            for (ScanResult result : mWifiScanResults) {
                bestResult = (bestResult == null) ? result : (result.level > bestResult.level) ? result : bestResult;
            }
            if (bestResult == null) {
                //TODO: show something?
                return;
            }
            locateBSSID(bestResult);
        }
    }

    private void refreshWifiList() {
        mWifiScanResults = getWifiBSSIDList();
        mNavigationDrawerFragment.updateList(mWifiScanResults);
    }

    private void locateBSSID(final ScanResult bestResultF) {
        updateActionBar("Locating " + bestResultF.SSID);

        // getting location info based on BSSID
        RequestParams params = new RequestParams();
        params.put("v", "1.1");
        params.put("bssid", bestResultF.BSSID);
        mHttpClient.get("http://api.mylnikov.org/wifi/", params, new JsonHttpResponseHandler() {
            /**
             * Returns when request succeeds
             *
             * @param statusCode http response status line
             * @param headers    response headers if any
             * @param response   parsed response if any
             */
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                super.onSuccess(statusCode, headers, response);
                Log.d("BSSID", String.format("status: %d, response: %s", statusCode, (null == response) ? "" : response.toString()));
                if (200 == statusCode) {
                    try {
                        String lat;
                        String lon;
                        JSONObject jsonObj = response.getJSONObject("data");
                        if (null != jsonObj) {
                            lat = jsonObj.getString("lat");
                            lon = jsonObj.getString("lon");
//                            final String targetMapUrl = String.format("http://maps.google.com/?ll=%s,%s", lat, lon);
                            final String targetMapUrl = String.format("http://www.google.com/maps?q=%s,%s", lat, lon);
                            Log.d("BSSID", "targetMapUrl=" + targetMapUrl);
                            mWebView.loadUrl(targetMapUrl);
                            updateActionBar(bestResultF.SSID + " located");
                            findViewById(R.id.progressBar).setVisibility(View.GONE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            /**
             * Returns when request failed
             *
             * @param statusCode    http response status line
             * @param headers       response headers if any
             * @param throwable     throwable describing the way request failed
             * @param errorResponse parsed response if any
             */
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                Log.d("BSSID", String.format("status: %d, error: %s", statusCode, (null==errorResponse)? "" : errorResponse.toString()));
                updateActionBar(bestResultF.SSID + " locates failed");
                findViewById(R.id.progressBar).setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Loading current location failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mWebView.stopLoading();
    }

    public List<ScanResult> getWifiBSSIDList() {
        WifiManager wifiManager = (WifiManager)
                getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> results = wifiManager.getScanResults();
        StringBuilder sb = new StringBuilder("");
        String message = "No results. Check wireless is on";
        if (results != null) {
            final int size = results.size();
            if (size == 0) message = "No access points in range";
            else {
                ScanResult bestSignal = results.get(0);
                int count = 1;
                for (ScanResult result : results) {
                    sb.append(count++ + ". " + result.SSID + " : "
                            + result.level + "\n" + result.BSSID + "\n"
                            + result.capabilities + "\n"
                            + "\n=======================\n");
                    if (WifiManager.compareSignalLevel(bestSignal.level,
                            result.level) < 0) {
                        bestSignal = result;
                    }
                }
                Log.d("BSSID", sb.toString());
                message = String.format(
                        "%s networks found. %s is the strongest.", size,
                        bestSignal.SSID + " : " + bestSignal.level);
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        return results;
    }

    public SSLContext getSslContext() {

        TrustManager[] byPassTrustManagers = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }
        } };

        SSLContext sslContext=null;

        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            sslContext.init(null, byPassTrustManagers, new SecureRandom());
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        return sslContext;
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

}
