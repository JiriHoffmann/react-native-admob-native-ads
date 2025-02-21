package com.ammarahmed.rnadmob.nativeads;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.CatalystInstance;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.events.RCTEventEmitter; 

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.ads.mediation.facebook.FacebookAdapter;
import com.google.ads.mediation.facebook.FacebookExtras;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.MediaContent;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class RNAdmobNativeView extends LinearLayout {

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };
    private int mediaAspectRatio = 1;
    private Runnable runnableForMount = null;
    ReactContext mContext;
    NativeAdView nativeAdView;
    NativeAd nativeAd;
    VideoOptions videoOptions;
    Bundle facebookExtras;
    AdManagerAdRequest.Builder adRequest;
    NativeAdOptions.Builder adOptions;
    AdLoader.Builder builder;
    AdLoader adLoader;
    RNAdmobMediaView mediaView;
    protected @Nullable
    String messagingModuleName;
    private boolean loadingAd = false;

    private int adChoicesPlacement = 1;
    private boolean requestNonPersonalizedAdsOnly = false;

    AdListener adListener = new AdListener() {

        @Override
        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            super.onAdFailedToLoad(loadAdError);
            WritableMap event = Arguments.createMap();
            WritableMap error = Arguments.createMap();
            error.putString("message", loadAdError.getMessage());
            error.putInt("code", loadAdError.getCode());
            error.putString("domain", loadAdError.getDomain());
            event.putMap("error",error);
            loadingAd = false;
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_FAILED_TO_LOAD, event);
        }


        @Override
        public void onAdClosed() {
            super.onAdClosed();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_CLOSED, null);
        }

        @Override
        public void onAdOpened() {
            super.onAdOpened();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_OPENED, null);
        }

        @Override
        public void onAdClicked() {
            super.onAdClicked();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_CLICKED, null);

        }

        @Override
        public void onAdLoaded() {
            super.onAdLoaded();
            loadingAd = false;
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_LOADED, null);
        }

        @Override
        public void onAdImpression() {
            super.onAdImpression();
            sendEvent(RNAdmobNativeViewManager.EVENT_AD_IMPRESSION, null);
        }
    };
    private String admobAdUnitId = "";
    private Handler handler;

    NativeAd.OnNativeAdLoadedListener onNativeAdLoadedListener = new NativeAd.OnNativeAdLoadedListener() {
        @Override
        public void onNativeAdLoaded(NativeAd ad) {
            if (nativeAd != null) {
                nativeAd.destroy();
            }

            if (ad != null) {
                nativeAd = ad;
                setNativeAd();
            }
            loadingAd = false;
            setNativeAdToJS(ad);
        }
    };


    public RNAdmobNativeView(ReactContext context) {
        super(context);
        mContext = context;
        createView(context);
        handler = new Handler();
        mCatalystInstance = mContext.getCatalystInstance();
        setId(UUID.randomUUID().hashCode() + this.getId());
        videoOptions = new VideoOptions.Builder().build();
        adRequest = new AdManagerAdRequest.Builder();
        adOptions = new NativeAdOptions.Builder();

    }

    public void createView(Context context) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View viewRoot = layoutInflater.inflate(R.layout.rn_ad_unified_native_ad, this, true);
        nativeAdView = (NativeAdView) viewRoot.findViewById(R.id.native_ad_view);

    }

    public void addMediaView(int id) {

        try {
            mediaView = (RNAdmobMediaView) nativeAdView.findViewById(id);
            if (mediaView != null) {
                nativeAd.getMediaContent().getVideoController().setVideoLifecycleCallbacks(mediaView.videoLifecycleCallbacks);
                nativeAdView.setMediaView((MediaView) nativeAdView.findViewById(id));
                mediaView.requestLayout();
                setNativeAd();

            }
        } catch (Exception e) {

        }
    }


    private Method getDeclaredMethod(Object obj, String name) {
        try {
            return obj.getClass().getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void setNativeAdToJS(NativeAd nativeAd) {

        try {
            WritableMap args = Arguments.createMap();
            args.putString("headline", nativeAd.getHeadline());
            args.putString("tagline", nativeAd.getBody());
            args.putString("advertiser", nativeAd.getAdvertiser());
            args.putString("callToAction", nativeAd.getCallToAction());
            args.putBoolean("video", nativeAd.getMediaContent().hasVideoContent());


            if (nativeAd.getPrice() != null) {
                args.putString("price", nativeAd.getPrice());
            }

            if (nativeAd.getStore() != null) {
                args.putString("store", nativeAd.getStore());
            }
            if (nativeAd.getStarRating() != null) {
                args.putInt("rating", nativeAd.getStarRating().intValue());
            }

            float aspectRatio = 1.0f;

            MediaContent mediaContent = nativeAd.getMediaContent();
            if (nativeAdView.getMediaView() != null) {
                nativeAdView.getMediaView().setMediaContent(nativeAd.getMediaContent());
            }


            if (mediaContent != null && null != getDeclaredMethod(mediaContent, "getAspectRatio")) {
                aspectRatio = nativeAd.getMediaContent().getAspectRatio();
                if (aspectRatio > 0) {
                    args.putString("aspectRatio", String.valueOf(aspectRatio));
                } else {
                    args.putString("aspectRatio", String.valueOf(1.0f));
                }

            } else {
                args.putString("aspectRatio", String.valueOf(1.0f));
            }


            WritableArray images = new WritableNativeArray();

            if (nativeAd.getImages() != null && nativeAd.getImages().size() > 0) {

                for (int i = 0; i < nativeAd.getImages().size(); i++) {
                    WritableMap map = Arguments.createMap();
                    if (nativeAd.getImages().get(i) != null) {
                        map.putString("url", nativeAd.getImages().get(i).getUri().toString());
                        map.putInt("width", 0);
                        map.putInt("height", 0);
                        images.pushMap(map);
                    }
                }
            }

            if (images != null) {
                args.putArray("images", images);
            } else {
                args.putArray("images", null);
            }

            if (nativeAd.getIcon() != null) {
                if (nativeAd.getIcon().getUri() != null) {
                    args.putString("icon", nativeAd.getIcon().getUri().toString());
                } else {
                    args.putString("icon", "empty");
                }
            } else {
                args.putString("icon", "noicon");
            }

            sendDirectMessage(args);

        } catch (Exception e) {
        }
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }


    public void setMessagingModuleName(String moduleName) {
        messagingModuleName = moduleName;
    }

    CatalystInstance mCatalystInstance;

    protected void sendDirectMessage(WritableMap data) {

        WritableNativeMap event = new WritableNativeMap();
        event.putMap("nativeEvent", data);
        WritableNativeArray params = new WritableNativeArray();
        params.pushMap(event);

        if (mCatalystInstance != null) {
            mCatalystInstance.callFunction(messagingModuleName, "onNativeAdLoaded", params);
        }

    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        loadingAd = false;
    }

    public void sendEvent(String name, @Nullable WritableMap event) {

        ReactContext reactContext = (ReactContext) mContext;
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                name,
                event);
    }

    public void loadAd() {
        if (loadingAd) return;
        try {
            loadingAd = true;
            adLoader.loadAd(adRequest.build());


        } catch (Exception e) {loadingAd = false;
        }

    }


    public void addNewView(View child, int index) {
        try {
            nativeAdView.addView(child, index);
            requestLayout();
            nativeAdView.requestLayout();
        } catch (Exception e) {

        }

    }

    @Override
    public void addView(View child) {
        super.addView(child);
        requestLayout();
    }


    public void setAdUnitId(String id) {
        admobAdUnitId = id;
        if (id == null) return;
        loadAdBuilder();
    }

    public void loadAdBuilder() {
        builder = new AdLoader.Builder(mContext, admobAdUnitId);
        builder.forNativeAd(onNativeAdLoadedListener);
        builder.withNativeAdOptions(adOptions.build());
        adLoader = builder.withAdListener(adListener)
                .build();
    }

    public void setAdChoicesPlacement(int location) {
        adChoicesPlacement = location;
        adOptions.setAdChoicesPlacement(adChoicesPlacement);
    }

    public void setRequestNonPersonalizedAdsOnly(boolean npa) {
        requestNonPersonalizedAdsOnly = npa;
        Bundle extras = new Bundle();
        if (requestNonPersonalizedAdsOnly) {
            extras.putString("npa", "1");
            adRequest.addNetworkExtrasBundle(AdMobAdapter.class, extras);
        } else {
            extras.putString("npa", "0");
            adRequest.addNetworkExtrasBundle(AdMobAdapter.class, extras);
        }

    }

    public void setMediaAspectRatio(int type) {
        mediaAspectRatio = type;
        adOptions.setMediaAspectRatio(mediaAspectRatio);
    }

    public void setNativeAd() {
        if (nativeAd != null) {
            if (handler != null && runnableForMount != null) {
                handler.removeCallbacks(runnableForMount);
                runnableForMount = null;
            }
            runnableForMount = new Runnable() {
                @Override
                public void run() {
                    if (nativeAdView != null && nativeAd != null) {
                        nativeAdView.setNativeAd(nativeAd);

                        if (mediaView != null && nativeAdView.getMediaView()!=null) {
                            nativeAdView.getMediaView().setMediaContent(nativeAd.getMediaContent());
                            if (nativeAd.getMediaContent().hasVideoContent()) {
                                mediaView.setVideoController(nativeAd.getMediaContent().getVideoController());
                                mediaView.setMedia(nativeAd.getMediaContent());
                            }
                        }

                    }
                }
            };
            if (handler != null ) {
                handler.postDelayed(runnableForMount, 1000);
            }

        }
    }


    public void setVideoOptions(ReadableMap options) {
        VideoOptions.Builder builder = new VideoOptions.Builder();

        if (options.hasKey("muted")) {
            builder.setStartMuted(options.getBoolean("muted"));
        }

        if (options.hasKey("clickToExpand")) {
            builder.setClickToExpandRequested(options.getBoolean("clickToExpand"));
        }

        if (options.hasKey("clickToExpand")) {
            builder.setCustomControlsRequested(options.getBoolean("clickToExpand"));
        }
        videoOptions = builder.build();

        adOptions.setVideoOptions(videoOptions);
    }

    public void setTargetingOptions(ReadableMap options) {
        VideoOptions.Builder builder = new VideoOptions.Builder();

        if (options.hasKey("targets")) {
            ReadableArray targets = options.getArray("targets");
            for (int i = 0; i < targets.size(); i++) {
                ReadableMap target = targets.getMap(i);
                String key = target.getString("key");
                if (target.getType("value") == ReadableType.Array) {
                    List list = Arguments.toList(target.getArray("value"));
                    adRequest.addCustomTargeting(key, list);
                } else {
                    adRequest.addCustomTargeting(key, target.getString("value"));
                }
            }
        }
        if (options.hasKey("categoryExclusions")) {
            ReadableArray categoryExclusions = options.getArray("categoryExclusions");
            for (int i = 0; i < categoryExclusions.size(); i++) {
                adRequest.addCategoryExclusion(categoryExclusions.getString(i));
            }
        }
        if (options.hasKey("publisherId")) {
            adRequest.setPublisherProvidedId(options.getString("publisherId"));
        }
        if (options.hasKey("requestAgent")) {
            adRequest.setRequestAgent(options.getString("requestAgent"));
        }
        if (options.hasKey("keywords")) {
            ReadableArray keywords = options.getArray("keywords");
            for (int i = 0; i < keywords.size(); i++) {
                adRequest.addKeyword(keywords.getString(i));
            }
        }
        if (options.hasKey("contentUrl")) {
            adRequest.setContentUrl(options.getString("contentUrl"));
        }
        if (options.hasKey("neighboringContentUrls")) {
            List list = Arguments.toList(options.getArray("neighboringContentUrls"));
            adRequest.setNeighboringContentUrls(list);
        }
    }


    public void setMediationOptions(ReadableMap options) {
        if (options.hasKey("nativeBanner")) {
            facebookExtras = new FacebookExtras().setNativeBanner(options.getBoolean("nativeBanner")).build();
            adRequest.addNetworkExtrasBundle(FacebookAdapter.class, facebookExtras);
        }

    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    public void removeHandler() {
        loadingAd = false;
        if (handler != null) {
            handler.removeCallbacks(runnableForMount);
            runnableForMount = null;
            handler = null;
        }
    }
}
