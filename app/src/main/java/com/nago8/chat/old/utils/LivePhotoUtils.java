package com.nago8.chat.old.utils;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 * 实况图片（Motion Photo / Live Photo）解析与提取工具类
 * 兼容 Android 4.4 (API 19) 及更高版本
 */
public class LivePhotoUtils {

    private static final String TAG = "LivePhotoUtils";

    // MP4 'ftyp' 标识字节 [0x66, 0x74, 0x79, 0x70]
    private static final byte[] FTYP_BYTES = new byte[]{'f', 't', 'y', 'p'};

    /**
     * 检查给定的图片文件是否包含内嵌的 MP4 实况视频，若包含则提取出 MP4 文件并返回
     * @param imageFile 下载到本地的临时图片文件
     * @param cacheDir 缓存目录
     * @return 提取出的 MP4 视频文件，若非实况图片则返回 null
     */
    public static File extractLiveVideo(File imageFile, File cacheDir) {
        if (imageFile == null || !imageFile.exists() || imageFile.length() < 1024) {
            return null;
        }

        long fileLength = imageFile.length();
        long videoOffset = findMp4StartOffset(imageFile, fileLength);

        if (videoOffset <= 0 || videoOffset >= fileLength) {
            return null;
        }

        long videoSize = fileLength - videoOffset;
        if (videoSize < 512) {
            return null;
        }

        File videoFile = new File(cacheDir, "live_video_" + System.nanoTime() + ".mp4");
        RandomAccessFile raf = null;
        FileOutputStream fos = null;

        try {
            raf = new RandomAccessFile(imageFile, "r");
            raf.seek(videoOffset);

            fos = new FileOutputStream(videoFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = raf.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            Log.d(TAG, "Successfully extracted live video: " + videoFile.getAbsolutePath() + ", size=" + videoSize);
            return videoFile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract live video", e);
            if (videoFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                videoFile.delete();
            }
            return null;
        } finally {
            try {
                if (fos != null) fos.close();
                if (raf != null) raf.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 查找 JPEG 中 MP4 文件的起始字节偏移量
     */
    private static long findMp4StartOffset(File file, long fileLength) {
        // 1. 尝试从尾部向前快速扫描（大多数 Motion Photo 的 MP4 位于文件末尾）
        // 扫描从 100KB 前或从文件一半处开始
        long searchStart = Math.max(0, fileLength - 100 * 1024 * 1024); // 最多检查前 100MB
        long offset = scanForFtyp(file, searchStart, fileLength);
        if (offset > 0) {
            return offset;
        }

        // 2. 如果尾部未找到，全文件扫描
        return scanForFtyp(file, 0, fileLength);
    }

    private static long scanForFtyp(File file, long start, long length) {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            if (start > 0) {
                long skipped = bis.skip(start);
                if (skipped < start) return -1;
            }

            int b;
            long currentPos = start;
            int matchIndex = 0;

            // 维护前4个字节以计算 box length
            byte[] prev4Bytes = new byte[4];
            int prevCount = 0;

            while ((b = bis.read()) != -1) {
                byte currentByte = (byte) b;

                // 记录滑动窗口
                prev4Bytes[0] = prev4Bytes[1];
                prev4Bytes[1] = prev4Bytes[2];
                prev4Bytes[2] = prev4Bytes[3];
                prev4Bytes[3] = currentByte;
                if (prevCount < 4) prevCount++;

                if (currentByte == FTYP_BYTES[matchIndex]) {
                    matchIndex++;
                    if (matchIndex == FTYP_BYTES.length) {
                        // 找到了 "ftyp"！
                        // "ftyp" 之前应该有 4 个字节的 Box Size
                        // currentPos 当前指向 'p'，所以 ftyp 起始位置为 currentPos - 3
                        long ftypPos = currentPos - 3;
                        long boxStart = ftypPos - 4;

                        if (boxStart >= 0) {
                            return boxStart;
                        }
                    }
                } else {
                    if (currentByte == FTYP_BYTES[0]) {
                        matchIndex = 1;
                    } else {
                        matchIndex = 0;
                    }
                }

                currentPos++;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning for ftyp in file", e);
        } finally {
            try {
                if (bis != null) bis.close();
            } catch (Exception ignored) {}
        }
        return -1;
    }
}
