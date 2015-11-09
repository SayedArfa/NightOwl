package com.asha.nightowllib;

import android.app.Activity;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.asha.nightowllib.handler.ISkinHandler;
import com.asha.nightowllib.inflater.Factory4InjectedInflater;
import com.asha.nightowllib.observer.IOwlObserver;
import com.asha.nightowllib.observer.OwlObservable;
import com.asha.nightowllib.observer.impls.NavBarObserver;
import com.asha.nightowllib.observer.impls.StatusBarObserver;
import com.asha.nightowllib.paint.ColorBox;

import java.util.concurrent.atomic.AtomicInteger;

import static com.asha.nightowllib.NightOwlUtil.checkBeforeLollipop;
import static com.asha.nightowllib.NightOwlUtil.checkHandler;
import static com.asha.nightowllib.NightOwlUtil.checkNonNull;
import static com.asha.nightowllib.NightOwlUtil.checkViewCollected;
import static com.asha.nightowllib.NightOwlUtil.injectLayoutInflater;
import static com.asha.nightowllib.NightOwlUtil.insertEmptyBox;
import static com.asha.nightowllib.NightOwlUtil.insertObservable;
import static com.asha.nightowllib.NightOwlUtil.obtainObservable;
import static com.asha.nightowllib.NightOwlUtil.obtainSkinBox;
import static com.asha.nightowllib.handler.HandlerManager.queryHandler;

/**
 * Created by hzqiujiadi on 15/11/5.
 * hzqiujiadi ashqalcn@gmail.com
 */
public class NightOwl {
    private static final String TAG = "NightOwl";
    private static final String WINDOW_INFLATER = "mLayoutInflater";
    private static final String THEME_INFLATER = "mInflater";
    private AtomicInteger mMode = new AtomicInteger(0);
    private static NightOwl sInstance;
    static {
        NightOwlTable.init();
    }

    private NightOwl(){
    }

    public static void owlBeforeCreate(Activity activity){
        Window window = activity.getWindow();
        LayoutInflater layoutInflater = window.getLayoutInflater();

        LayoutInflater injectLayoutInflater1 = Factory4InjectedInflater.newInstance(layoutInflater, activity);
        injectLayoutInflater(injectLayoutInflater1
                , activity.getWindow()
                , activity.getWindow().getClass()
                , WINDOW_INFLATER);

        LayoutInflater injectLayoutInflater2 = injectLayoutInflater1.cloneInContext(activity);
        injectLayoutInflater(injectLayoutInflater2
                , activity
                , ContextThemeWrapper.class
                , THEME_INFLATER);
    }

    public static void owlAfterCreate(Activity activity){
        // not support before lollipop.
        if ( checkBeforeLollipop() ) return;

        View v = activity.getWindow().getDecorView();
        OwlObservable owlObservable = new OwlObservable();
        Resources.Theme theme = activity.getTheme();
        TypedArray a = theme.obtainStyledAttributes(R.styleable.NightOwl_Theme);
        if ( a != null ){
            int n = a.getIndexCount();
            for (int i = 0; i < n; i++) {
                int attr = a.getIndex(i);
                if ( attr == R.styleable.NightOwl_Theme_night_navigationBarColor ){
                    owlObservable.registerObserver(new NavBarObserver(activity, a, attr));
                } else if ( attr == R.styleable.NightOwl_Theme_night_statusBarColor ){
                    owlObservable.registerObserver(new StatusBarObserver(activity, a, attr));
                }
            }
            a.recycle();
        }
        insertObservable(v, owlObservable);

        // init set
        owlObservable.notifyObserver(sharedInstance().mMode.get(), activity);
    }

    public static void owlDressUp(int mode, @NonNull Activity activity){
        // View tree
        NightOwl owl = sharedInstance();
        if ( owl.mMode.get() != mode ){
            View root = activity.getWindow().getDecorView();
            innerRefreshSkin( mode, root, activity);
            owl.mMode.set(mode);
        }

        // not support before lollipop.
        if ( checkBeforeLollipop() ) return;
        // OwlObservable
        View v = activity.getWindow().getDecorView();
        OwlObservable observable = obtainObservable(v);
        if ( observable != null ) observable.notifyObserver(mode, activity);
    }

    /**
     * Register a custom view which created by new instance directly.
     *
     * @param view instanceof IOwlObserver & View
     *             NightOwl will trigger view.onSkinChange immediately.
     */
    public static void owlRegView( @NonNull IOwlObserver view ){
        if ( view instanceof View ) {
            View target = (View) view;
            insertEmptyBox(target);
            view.onSkinChange(owlCurrentMode(), null);
        } else {
            throw new IllegalArgumentException("owlAttach param must be a instance of View");
        }
    }

    public static int owlCurrentMode(){
        return sharedInstance().mMode.get();
    }

    private static void innerRefreshSkin(int mode, View view , Activity activity){
        // refresh current view
        if ( checkViewCollected(view) ){
            ColorBox box = obtainSkinBox(view);
            if ( box != null ) box.refreshSkin(mode, view);
            if ( view instanceof IOwlObserver ){
                ((IOwlObserver) view).onSkinChange(mode,activity);
            }
        }
        // traversal view tree
        if ( view instanceof ViewGroup){
            ViewGroup vg = (ViewGroup) view;
            View sub;
            for (int i = 0; i < vg.getChildCount(); i++) {
                sub = vg.getChildAt(i);
                innerRefreshSkin(mode, sub, activity);
            }
        }
    }

    private static void owlRegViewClz(Class<? extends View> clz){
        //HandlerManager.registerView(clz);
    }

    public static void handleViewCreated(@NonNull View view, @NonNull AttributeSet attrs) {
        // check the view has been collected
        if ( checkViewCollected(view) ) return;

        // query the handler
        ISkinHandler handler = queryHandler(view.getClass());
        if ( !checkHandler(handler,view) ) return;

        NightOwl owl = sharedInstance();

        int mode = owl.mMode.get();

        // do collect
        handler.collect(owl.mMode.get(), view, view.getContext(), attrs);

        // if view is instanceof IOwlObserver
        // and not be collected
        if ( view instanceof IOwlObserver ){
            if (  !checkViewCollected(view) ) insertEmptyBox(view);
            // we can't get the activity here
            // beacuse the view.getContext may return ContextThemeWrapper
            // so we call with null
            ((IOwlObserver) view).onSkinChange( mode, null );
        }
    }

    private static NightOwl sharedInstance(){
        checkNonNull(sInstance,"You must create NightOwl in Application onCreate.");
        return sInstance;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder {
        private int mode;
        public Builder defualt(int mode){
            this.mode = mode;
            return this;
        }
        public NightOwl create(){
            if ( sInstance != null ) throw new RuntimeException("Do not create NightOwl again.");
            sInstance = new NightOwl();
            sInstance.mMode.set(mode);
            return sInstance;
        }
    }
}
