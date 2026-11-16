package com.motoroll.home;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Motoroll Home 3.0 - lightweight, independent HOME launcher.
 * Native views only: no network, analytics, background service, or third-party UI library.
 * App drawer opens with a downward swipe on the home screen. An upward swipe closes it.
 */
public class MainActivity extends Activity {
    private static final String TAG = "MotorollHome";
    private static final int REQUEST_WALLPAPER = 3107;
    private static final String PREFS = "motoroll_home_prefs";
    private static final String PREF_WALLPAPER_URI = "launcher_wallpaper_uri";

    private static final int BG = Color.rgb(5, 7, 13);
    private static final int PANEL = Color.rgb(15, 18, 31);
    private static final int PANEL_ALT = Color.rgb(22, 25, 43);
    private static final int TEXT = Color.rgb(246, 247, 255);
    private static final int MUTED = Color.rgb(149, 158, 181);
    private static final int BLUE = Color.rgb(65, 137, 255);
    private static final int CYAN = Color.rgb(94, 193, 255);
    private static final int PURPLE = Color.rgb(145, 94, 255);
    private static final int STROKE = Color.rgb(45, 49, 76);

    // Preferred home shortcuts. Missing packages are simply skipped.
    private static final String[] HOME_FAVORITES = {
            "com.google.android.apps.photos",
            "com.android.vending",
            "com.google.android.apps.maps",
            "com.google.android.gm",
            "com.android.chrome",
            "com.google.android.apps.messaging",
            "com.google.android.dialer",
            "com.android.settings"
    };

    // The camera and GameTime are intentionally not pinned as launcher menu shortcuts.
    private static final String[][] DOCK_PACKAGES = {
            {"com.google.android.dialer", "com.android.dialer"},
            {"com.google.android.apps.messaging", "com.android.mms"},
            {"com.android.chrome"},
            {"com.android.settings"}
    };

    private final List<ResolveInfo> apps = new ArrayList<>();
    private GridView homeGrid;
    private GridView drawerGrid;
    private EditText drawerSearch;
    private TextView drawerCount;
    private LinearLayout dock;
    private boolean drawerOpen = false;
    private float downX;
    private float downY;
    private boolean trackingGesture;
    private Bitmap wallpaperBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        try {
            loadApps();
            showHome(false);
        } catch (RuntimeException error) {
            Log.e(TAG, "Launcher failed during startup", error);
            showStartupError(error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            loadApps();
            if (drawerOpen && drawerSearch != null) {
                String q = drawerSearch.getText() == null ? "" : drawerSearch.getText().toString();
                refreshDrawer(q);
            } else if (homeGrid != null) {
                homeGrid.setAdapter(new AppAdapter(favoriteApps(), true));
                rebuildDock();
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not refresh launcher apps", error);
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
            window.getInsetsController().setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams lp(int widthDp, int heightDp) {
        int w = widthDp < 0 ? widthDp : dp(widthDp);
        int h = heightDp < 0 ? heightDp : dp(heightDp);
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams withTop(LinearLayout.LayoutParams params, int marginDp) {
        params.topMargin = dp(marginDp);
        return params;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setIncludeFontPadding(false);
        view.setTypeface(android.graphics.Typeface.create(
                "sans-serif", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void configureInsets(View view, int horizontalDp, int verticalTopDp, int verticalBottomDp) {
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int left = 0, top = 0, right = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            target.setPadding(dp(horizontalDp) + left, dp(verticalTopDp) + top,
                    dp(horizontalDp) + right, dp(verticalBottomDp) + bottom);
            return insets;
        });
    }

    private FrameLayout createRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        Uri saved = getSavedWallpaperUri();
        if (saved != null) {
            if (wallpaperBitmap == null) wallpaperBitmap = decodeWallpaper(saved);
            if (wallpaperBitmap != null) {
                ImageView wallpaper = new ImageView(this);
                wallpaper.setImageBitmap(wallpaperBitmap);
                wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
                root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
                View scrim = new View(this);
                scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0x76030712, 0x4205070D, 0x99030610}));
                root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
                return root;
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_WALLPAPER_URI).apply();
        }

        root.addView(new AmbientBackground(this), new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private Uri getSavedWallpaperUri() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_WALLPAPER_URI, null);
        if (value == null || value.trim().isEmpty()) return null;
        try { return Uri.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private Bitmap decodeWallpaper(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int targetW = Math.max(720, getResources().getDisplayMetrics().widthPixels);
            int targetH = Math.max(1400, getResources().getDisplayMetrics().heightPixels);
            int sample = 1;
            while ((bounds.outWidth / (sample * 2)) >= targetW
                    && (bounds.outHeight / (sample * 2)) >= targetH) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not load selected wallpaper", error);
            return null;
        }
    }

    private void chooseWallpaper() {
        try {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("image/*");
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(picker, REQUEST_WALLPAPER);
        } catch (Exception error) {
            Toast.makeText(this, "No se pudo abrir el selector de fondos", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Wallpaper picker unavailable", error);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some document providers do not offer persistable grants. The current grant may still work.
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_WALLPAPER_URI, uri.toString()).apply();
        wallpaperBitmap = null;
        showHome(false);
        Toast.makeText(this, "Fondo de Motoroll actualizado", Toast.LENGTH_SHORT).show();
    }

    private void loadApps() {
        apps.clear();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(query, 0);
        } catch (RuntimeException error) {
            Log.e(TAG, "PackageManager could not enumerate launcher apps", error);
            found = Collections.emptyList();
        }
        Set<String> seen = new HashSet<>();
        if (found != null) {
            for (ResolveInfo info : found) {
                if (info == null || info.activityInfo == null) continue;
                String packageName = info.activityInfo.packageName;
                if (packageName == null || getPackageName().equals(packageName)) continue;
                String key = packageName + "/" + info.activityInfo.name;
                if (seen.add(key)) apps.add(info);
            }
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> collator.compare(safeLabel(a), safeLabel(b)));
    }

    private String safeLabel(ResolveInfo info) {
        try {
            CharSequence label = info.loadLabel(getPackageManager());
            return label == null ? info.activityInfo.packageName : label.toString();
        } catch (RuntimeException error) {
            return info.activityInfo == null ? "Aplicación" : info.activityInfo.packageName;
        }
    }

    private String compactLabel(ResolveInfo info) {
        String value = safeLabel(info);
        return value.length() > 14 ? value.substring(0, 13).trim() + "…" : value;
    }

    private List<ResolveInfo> favoriteApps() {
        List<ResolveInfo> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String preferred : HOME_FAVORITES) {
            ResolveInfo match = firstPackage(preferred);
            if (match != null && !isExcludedFromFavorites(match)
                    && used.add(match.activityInfo.packageName)) result.add(match);
        }
        for (ResolveInfo info : apps) {
            if (result.size() >= 8) break;
            if (!isExcludedFromFavorites(info) && used.add(info.activityInfo.packageName)) result.add(info);
        }
        return result;
    }

    private boolean isExcludedFromFavorites(ResolveInfo info) {
        String p = info.activityInfo.packageName.toLowerCase(Locale.ROOT);
        return p.contains("gamemode") || p.contains("gameassistant") || p.contains("camera5")
                || p.contains("camera");
    }

    private ResolveInfo firstPackage(String packageName) {
        for (ResolveInfo info : apps) {
            if (packageName.equals(info.activityInfo.packageName)) return info;
        }
        return null;
    }

    private ResolveInfo firstPackage(String[] candidates) {
        for (String candidate : candidates) {
            ResolveInfo found = firstPackage(candidate);
            if (found != null) return found;
        }
        return null;
    }

    private void showHome(boolean animate) {
        drawerOpen = false;
        drawerSearch = null;
        drawerGrid = null;
        drawerCount = null;
        homeGrid = null;

        FrameLayout root = createRoot();
        LinearLayout shell = column();
        configureInsets(shell, 20, 12, 10);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        shell.addView(buildTopBar(), lp(-1, 48));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = column();
        content.setPadding(0, dp(23), 0, dp(12));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView label = text("TU ESPACIO", 10, CYAN, true);
        label.setLetterSpacing(0.17f);
        content.addView(label);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextColor(TEXT);
        clock.setTextSize(62);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", 0));
        content.addView(clock, withTop(lp(-2, -2), 7));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextColor(MUTED);
        date.setTextSize(13);
        content.addView(date, withTop(lp(-2, -2), 1));

        LinearLayout favoritesHeader = row();
        TextView favoritesTitle = text("FAVORITAS", 11, TEXT, true);
        favoritesTitle.setLetterSpacing(0.1f);
        favoritesHeader.addView(favoritesTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView all = text("TODAS  ↓", 10, CYAN, true);
        all.setLetterSpacing(0.04f);
        favoritesHeader.addView(all);
        all.setOnClickListener(v -> showDrawer(true, ""));
        content.addView(favoritesHeader, withTop(lp(-1, -2), 29));

        homeGrid = new GridView(this);
        configureGrid(homeGrid, 4);
        homeGrid.setVerticalSpacing(dp(8));
        homeGrid.setAdapter(new AppAdapter(favoriteApps(), true));
        content.addView(homeGrid, withTop(lp(-1, 218), 12));

        View spacer = new View(this);
        content.addView(spacer, lp(-1, 5));

        shell.addView(buildSearchBar(), lp(-1, 51));
        dock = buildDock();
        shell.addView(dock, withTop(lp(-1, 70), 10));

        setContentView(root);
        shell.requestApplyInsets();
        if (animate) {
            shell.setAlpha(0.94f);
            shell.animate().alpha(1f).setDuration(150).start();
        }
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = row();
        MotorollSymbol symbol = new MotorollSymbol(this);
        bar.addView(symbol, lp(37, 37));
        LinearLayout identity = column();
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(dp(10), 0, 0, 0);
        TextView name = text("MOTOROLL", 14, TEXT, true);
        name.setLetterSpacing(0.12f);
        TextView sub = text("HOME EXPERIENCE", 8, MUTED, true);
        sub.setLetterSpacing(0.11f);
        identity.addView(name);
        identity.addView(sub, withTop(lp(-2, -2), 3));
        bar.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView wallpaper = text("FONDO", 10, CYAN, true);
        wallpaper.setGravity(Gravity.CENTER);
        wallpaper.setPadding(dp(13), dp(10), dp(13), dp(10));
        wallpaper.setBackground(rounded(PANEL_ALT, 18, STROKE, 1));
        wallpaper.setOnClickListener(v -> chooseWallpaper());
        bar.addView(wallpaper, lp(-2, 39));
        return bar;
    }

    private View buildSearchBar() {
        LinearLayout search = row();
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(rounded(0xE6101423, 19, 0x90424A70, 1));
        TextView icon = text("⌕", 25, CYAN, false);
        search.addView(icon, lp(29, -2));
        TextView hint = text("Buscar en tus aplicaciones", 12, MUTED, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, -2, 1f);
        hintParams.leftMargin = dp(8);
        search.addView(hint, hintParams);
        TextView arrow = text("↓", 17, PURPLE, true);
        search.addView(arrow);
        search.setOnClickListener(v -> showDrawer(true, ""));
        return search;
    }

    private LinearLayout buildDock() {
        LinearLayout dockView = row();
        dockView.setGravity(Gravity.CENTER);
        dockView.setPadding(dp(8), dp(7), dp(8), dp(7));
        dockView.setBackground(rounded(0xE6101423, 23, 0x80424A70, 1));
        Set<String> used = new HashSet<>();
        int added = 0;
        for (String[] choices : DOCK_PACKAGES) {
            ResolveInfo info = firstPackage(choices);
            if (info == null || !used.add(info.activityInfo.packageName)) continue;
            dockView.addView(createDockIcon(info), new LinearLayout.LayoutParams(0, -1, 1f));
            added++;
        }
        // Fill any missing dock position with non-camera, non-gaming apps.
        for (ResolveInfo info : favoriteApps()) {
            if (added >= 4) break;
            if (!used.add(info.activityInfo.packageName)) continue;
            dockView.addView(createDockIcon(info), new LinearLayout.LayoutParams(0, -1, 1f));
            added++;
        }
        return dockView;
    }

    private void rebuildDock() {
        if (dock == null || !(dock.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) dock.getParent();
        int index = parent.indexOfChild(dock);
        if (index < 0) return;
        LinearLayout replacement = buildDock();
        parent.removeViewAt(index);
        parent.addView(replacement, index, withTop(lp(-1, 70), 10));
        dock = replacement;
    }

    private View createDockIcon(ResolveInfo info) {
        FrameLayout holder = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(info.loadIcon(getPackageManager())); }
        catch (RuntimeException ignored) { icon.setImageResource(android.R.drawable.sym_def_app_icon); }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
        holder.addView(icon, iconParams);
        holder.setContentDescription(safeLabel(info));
        holder.setOnClickListener(v -> launchApp(info));
        return holder;
    }

    private void configureGrid(GridView grid, int columns) {
        grid.setNumColumns(columns);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(2));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setCacheColorHint(Color.TRANSPARENT);
        grid.setClipToPadding(false);
    }

    private class AppAdapter extends BaseAdapter {
        private final List<ResolveInfo> items;
        private final boolean homeStyle;

        AppAdapter(List<ResolveInfo> source, boolean home) {
            items = new ArrayList<>(source);
            homeStyle = home;
        }

        @Override public int getCount() { return items.size(); }
        @Override public ResolveInfo getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppViewHolder holder;
            LinearLayout cell;
            if (convertView == null) {
                cell = column();
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2), dp(4), dp(2), dp(4));
                cell.setMinimumHeight(dp(homeStyle ? 99 : 91));

                FrameLayout iconFrame = new FrameLayout(MainActivity.this);
                iconFrame.setBackground(rounded(0xC51A1D32, homeStyle ? 19 : 17, 0x70434D79, 1));
                ImageView image = new ImageView(MainActivity.this);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconFrame.addView(image, new FrameLayout.LayoutParams(dp(homeStyle ? 43 : 38),
                        dp(homeStyle ? 43 : 38), Gravity.CENTER));
                cell.addView(iconFrame, lp(homeStyle ? 61 : 56, homeStyle ? 57 : 53));

                TextView label = text("", homeStyle ? 10 : 10, TEXT, false);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(label, withTop(lp(-1, -2), 7));
                holder = new AppViewHolder(image, label);
                cell.setTag(holder);
            } else {
                cell = (LinearLayout) convertView;
                holder = (AppViewHolder) cell.getTag();
            }

            ResolveInfo item = getItem(position);
            holder.label.setText(compactLabel(item));
            try { holder.icon.setImageDrawable(item.loadIcon(getPackageManager())); }
            catch (RuntimeException error) { holder.icon.setImageResource(android.R.drawable.sym_def_app_icon); }
            cell.setContentDescription(safeLabel(item));
            cell.setOnClickListener(v -> launchApp(item));
            return cell;
        }
    }

    private static final class AppViewHolder {
        final ImageView icon;
        final TextView label;
        AppViewHolder(ImageView icon, TextView label) { this.icon = icon; this.label = label; }
    }

    private void showDrawer(boolean animate, String initialQuery) {
        drawerOpen = true;
        homeGrid = null;
        drawerSearch = null;
        drawerGrid = null;

        FrameLayout root = createRoot();
        LinearLayout shell = column();
        configureInsets(shell, 10, 7, 8);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = column();
        panel.setPadding(dp(17), dp(17), dp(17), dp(12));
        panel.setBackground(rounded(0xF40A0D19, 30, 0xA03D456B, 1));
        shell.addView(panel, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout header = row();
        LinearLayout titles = column();
        TextView title = text("APLICACIONES", 20, TEXT, true);
        title.setLetterSpacing(0.045f);
        TextView sub = text("TU BIBLIOTECA MOTOROLL", 8, CYAN, true);
        sub.setLetterSpacing(0.13f);
        titles.addView(title);
        titles.addView(sub, withTop(lp(-2, -2), 4));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView close = text("×", 29, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(rounded(PANEL_ALT, 18, STROKE, 1));
        header.addView(close, lp(42, 42));
        close.setOnClickListener(v -> showHome(true));
        panel.addView(header);

        drawerSearch = new EditText(this);
        drawerSearch.setSingleLine(true);
        drawerSearch.setTextSize(14);
        drawerSearch.setTextColor(TEXT);
        drawerSearch.setHintTextColor(MUTED);
        drawerSearch.setHint("Buscar una aplicación");
        drawerSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        drawerSearch.setPadding(dp(16), 0, dp(16), 0);
        drawerSearch.setBackground(rounded(PANEL_ALT, 18, STROKE, 1));
        panel.addView(drawerSearch, withTop(lp(-1, 52), 17));

        drawerCount = text("", 10, MUTED, false);
        panel.addView(drawerCount, withTop(lp(-1, 18), 14));

        drawerGrid = new GridView(this);
        configureGrid(drawerGrid, 4);
        drawerGrid.setVerticalSpacing(dp(6));
        panel.addView(drawerGrid, withTop(new LinearLayout.LayoutParams(-1, 0, 1f), 7));

        TextView footer = text("Desliza hacia arriba para volver al inicio", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        panel.addView(footer, withTop(lp(-1, 22), 5));

        setContentView(root);
        shell.requestApplyInsets();
        refreshDrawer(initialQuery == null ? "" : initialQuery);
        drawerSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshDrawer(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        if (animate) {
            panel.setAlpha(0.94f);
            panel.setTranslationY(-dp(62));
            panel.animate().alpha(1f).translationY(0f).setDuration(190).start();
        }
    }

    private void refreshDrawer(String query) {
        if (drawerGrid == null) return;
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<ResolveInfo> filtered = new ArrayList<>();
        for (ResolveInfo app : apps) {
            String label = safeLabel(app);
            if (needle.isEmpty() || label.toLowerCase(Locale.ROOT).contains(needle)) filtered.add(app);
        }
        if (drawerCount != null) drawerCount.setText(filtered.size() + " aplicaciones");
        drawerGrid.setAdapter(new AppAdapter(filtered, false));
    }

    private void launchApp(ResolveInfo info) {
        try {
            if (info == null || info.activityInfo == null || info.activityInfo.name == null) return;
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Log.e(TAG, "Could not launch application", error);
            Toast.makeText(this, "No se pudo abrir esa aplicación", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            trackingGesture = true;
        } else if (action == MotionEvent.ACTION_UP && trackingGesture) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            trackingGesture = false;
            boolean mostlyVertical = Math.abs(dx) < Math.max(dp(100), Math.abs(dy) * 0.72f);
            if (mostlyVertical && !drawerOpen && dy > dp(82)) {
                showDrawer(true, "");
                return true;
            }
            if (mostlyVertical && drawerOpen && dy < -dp(82)) {
                showHome(true);
                return true;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            trackingGesture = false;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (drawerOpen) showHome(true);
        else super.onBackPressed();
    }

    private void showStartupError(RuntimeException error) {
        LinearLayout screen = column();
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(dp(28), dp(28), dp(28), dp(28));
        screen.setBackgroundColor(BG);
        TextView title = text("Motoroll Home no pudo iniciarse", 20, TEXT, true);
        TextView detail = text(error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()),
                12, MUTED, false);
        detail.setPadding(0, dp(14), 0, dp(20));
        screen.addView(title);
        screen.addView(detail);
        TextView retry = text("Volver a intentar", 13, CYAN, true);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(rounded(PANEL_ALT, 16, STROKE, 1));
        screen.addView(retry, lp(210, 48));
        retry.setOnClickListener(v -> {
            try { loadApps(); showHome(true); }
            catch (RuntimeException again) { Log.e(TAG, "Retry failed", again); }
        });
        setContentView(screen);
    }

    private class AmbientBackground extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RadialGradient blueGlow;
        private RadialGradient purpleGlow;

        AmbientBackground(Context context) { super(context); }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            float main = Math.max(width, height) * 0.54f;
            blueGlow = new RadialGradient(width * 0.92f, height * 0.18f, main,
                    new int[]{0x30335FA8, 0x15142143, 0x0005070D},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            purpleGlow = new RadialGradient(width * 0.04f, height * 0.78f, main * 0.9f,
                    new int[]{0x2C57318D, 0x141A1038, 0x0005070D},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(BG);
            if (blueGlow != null) {
                paint.setShader(blueGlow);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
            if (purpleGlow != null) {
                paint.setShader(purpleGlow);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
            paint.setShader(null);
        }
    }

    private class MotorollSymbol extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        MotorollSymbol(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) * 0.43f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setShader(new android.graphics.LinearGradient(cx-r, cy-r, cx+r, cy+r,
                    new int[]{CYAN, BLUE, PURPLE}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            paint.setStrokeWidth(dp(2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setShader(new android.graphics.LinearGradient(cx-r, cy, cx+r, cy,
                    CYAN, PURPLE, Shader.TileMode.CLAMP));
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(cx-r*0.57f, cy-r*0.20f);
            path.lineTo(cx-r*0.27f, cy+r*0.29f);
            path.lineTo(cx, cy-r*0.02f);
            path.lineTo(cx+r*0.27f, cy+r*0.29f);
            path.lineTo(cx+r*0.57f, cy-r*0.20f);
            canvas.drawPath(path, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
