package com.zenxi.yogatimer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;
import android.util.SparseIntArray;

/**
 * 铃音播放。
 *
 * 关键点：使用闹钟声道（USAGE_ALARM）。这样即使手机静音、或者用户正在看视频，
 * 提示音依然会响，并且不会打断/暂停用户正在播放的视频。
 */
public final class SoundBank {

    private static final String TAG = "ZenXi/Sound";
    private static final SoundBank INSTANCE = new SoundBank();

    public static SoundBank get() {
        return INSTANCE;
    }

    public static final int TONE_BRASS = 0;
    public static final int TONE_CRYSTAL = 1;
    public static final int TONE_BOWL = 2;
    public static final int TONE_WOOD = 3;
    public static final int TONE_WATER = 4;

    public static final String[] TONE_NAMES = {"铜铃", "水晶", "颂钵", "木鱼", "水滴"};

    /** 起势音可选项；最后一项是静音。 */
    public static final String[] START_NAMES = {"双铃", "清铃", "滑音", "木鱼", "水滴", "静音"};

    private static final int[] DING = {
            R.raw.brass_ding, R.raw.crystal_ding, R.raw.bowl_ding,
            R.raw.wood_ding, R.raw.water_ding};

    private static final int[] DANG = {
            R.raw.brass_dang, R.raw.crystal_dang, R.raw.bowl_dang,
            R.raw.wood_dang, R.raw.water_dang};

    private static final int[] START = {
            R.raw.start, R.raw.start_single, R.raw.start_glide,
            R.raw.start_wood, R.raw.start_water, 0};

    private static final int[] ALL = {
            R.raw.brass_ding, R.raw.brass_dang,
            R.raw.crystal_ding, R.raw.crystal_dang,
            R.raw.bowl_ding, R.raw.bowl_dang,
            R.raw.wood_ding, R.raw.wood_dang,
            R.raw.water_ding, R.raw.water_dang,
            R.raw.tick, R.raw.start, R.raw.settle, R.raw.half,
            R.raw.breath_in, R.raw.breath_out,
            R.raw.start_single, R.raw.start_glide, R.raw.start_wood, R.raw.start_water};

    private SoundPool pool;
    private final SparseIntArray soundIds = new SparseIntArray();
    private boolean ready;

    private SoundBank() {
    }

    public synchronized void init(Context context) {
        if (pool != null) {
            return;
        }
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            SoundPool p = new SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(attrs)
                    .build();
            pool = p;
            for (int res : ALL) {
                soundIds.put(res, p.load(context, res, 1));
            }
            p.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                    if (status == 0) {
                        ready = true;
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "SoundPool init failed", t);
        }
    }

    /** 预加载完成前也不报错，只是静默。 */
    public void play(int rawRes, float volume) {
        SoundPool p = pool;
        if (p == null) {
            return;
        }
        int id = soundIds.get(rawRes, 0);
        if (id == 0) {
            return;
        }
        float v = Math.max(0f, Math.min(1f, volume));
        try {
            p.play(id, v, v, 1, 0, 1f);
        } catch (Throwable t) {
            Log.w(TAG, "play failed", t);
        }
    }

    public void ding(int tone, float volume) {
        play(DING[clamp(tone)], volume);
    }

    public void dang(int tone, float volume) {
        play(DANG[clamp(tone)], volume);
    }

    public void tick(float volume) {
        play(R.raw.tick, volume);
    }

    /** startTone: 0 双铃 1 清铃 2 滑音 3 木鱼 4 水滴 5 静音。 */
    public void start(int startTone, float volume) {
        int idx = startTone < 0 ? 0 : (startTone >= START.length ? START.length - 1 : startTone);
        int res = START[idx];
        if (res == 0) {
            return;
        }
        play(res, volume);
    }

    public void half(float volume) {
        play(R.raw.half, volume);
    }

    public void settle(float volume) {
        play(R.raw.settle, volume);
    }

    public void breathIn(float volume) {
        play(R.raw.breath_in, volume);
    }

    public void breathOut(float volume) {
        play(R.raw.breath_out, volume);
    }

    /** 试听某个音色（设置面板里用）。 */
    public void preview(int tone, float volume) {
        ding(tone, volume);
    }

    /** 试听起势音（设置面板里用）。 */
    public void previewStart(int startTone, float volume) {
        start(startTone, volume);
    }

    private static int clamp(int tone) {
        return tone < 0 ? 0 : (tone > 4 ? 4 : tone);
    }

    public void release() {
        SoundPool p = pool;
        pool = null;
        soundIds.clear();
        ready = false;
        if (p != null) {
            try {
                p.release();
            } catch (Throwable ignored) {
            }
        }
    }
}
