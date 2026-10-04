/*
 * RANN's Roost: HEIC photos on the desktop (CAP-03, ADR 0004).
 *
 * A small JNI library around libheif and libde265, linked statically so each platform ships one
 * file. It decodes the primary image of a HEIF file into ARGB pixels; libheif applies the file's
 * rotation and mirroring itself. Called by ca.schippers.hfm.ocr.desktop.HeifNative.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <libheif/heif.h>

static void throw_io(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) (*env)->ThrowNew(env, cls, message);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) vm;
    (void) reserved;
    heif_init(NULL);
    return JNI_VERSION_1_8;
}

JNIEXPORT jstring JNICALL Java_ca_schippers_hfm_ocr_desktop_HeifNative_version(JNIEnv *env, jclass cls) {
    (void) cls;
    return (*env)->NewStringUTF(env, heif_get_version());
}

/*
 * Returns [width, height, pixel...] with each pixel as 0xFFRRGGBB, or throws IOException. Files
 * over maxPixels are refused; pictures are reduced to at most maxSide pixels on the long side
 * here, so a 48-megapixel photo never becomes a 200 MB array in Java.
 */
JNIEXPORT jintArray JNICALL Java_ca_schippers_hfm_ocr_desktop_HeifNative_decode(JNIEnv *env, jclass cls, jbyteArray data, jint maxPixels, jint maxSide) {
    (void) cls;
    jsize length = (*env)->GetArrayLength(env, data);
    uint8_t *bytes = malloc(length > 0 ? (size_t) length : 1);
    if (bytes == NULL) {
        throw_io(env, "out of memory");
        return NULL;
    }
    (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte *) bytes);

    jintArray result = NULL;
    struct heif_context *ctx = heif_context_alloc();
    struct heif_image_handle *handle = NULL;
    struct heif_image *image = NULL;
    jint *row = NULL;
    struct heif_error err;

    if (ctx == NULL) {
        throw_io(env, "out of memory");
        goto done;
    }
    heif_context_get_security_limits(ctx)->max_image_size_pixels = (uint64_t) maxPixels;

    err = heif_context_read_from_memory_without_copy(ctx, bytes, (size_t) length, NULL);
    if (err.code != heif_error_Ok) {
        throw_io(env, err.message);
        goto done;
    }
    err = heif_context_get_primary_image_handle(ctx, &handle);
    if (err.code != heif_error_Ok) {
        throw_io(env, err.message);
        goto done;
    }
    if ((int64_t) heif_image_handle_get_width(handle) * heif_image_handle_get_height(handle) > maxPixels) {
        throw_io(env, "image too large");
        goto done;
    }
    err = heif_decode_image(handle, &image, heif_colorspace_RGB, heif_chroma_interleaved_RGB, NULL);
    if (err.code != heif_error_Ok) {
        throw_io(env, err.message);
        goto done;
    }

    int width = heif_image_get_width(image, heif_channel_interleaved);
    int height = heif_image_get_height(image, heif_channel_interleaved);
    int longSide = width > height ? width : height;
    if (maxSide > 0 && longSide > maxSide) {
        int w = (int) ((int64_t) width * maxSide / longSide);
        int h = (int) ((int64_t) height * maxSide / longSide);
        struct heif_image *scaled = NULL;
        err = heif_image_scale_image(image, &scaled, w > 0 ? w : 1, h > 0 ? h : 1, NULL);
        if (err.code != heif_error_Ok) {
            throw_io(env, err.message);
            goto done;
        }
        heif_image_release(image);
        image = scaled;
        width = heif_image_get_width(image, heif_channel_interleaved);
        height = heif_image_get_height(image, heif_channel_interleaved);
    }
    size_t stride = 0;
    const uint8_t *plane = heif_image_get_plane_readonly2(image, heif_channel_interleaved, &stride);
    if (plane == NULL || width <= 0 || height <= 0 || (int64_t) width * height > maxPixels) {
        throw_io(env, "no picture in the file");
        goto done;
    }

    result = (*env)->NewIntArray(env, 2 + width * height);
    row = malloc((size_t) width * sizeof(jint));
    if (result == NULL || row == NULL) {
        if (row == NULL && !(*env)->ExceptionCheck(env)) throw_io(env, "out of memory");
        result = NULL;
        goto done;
    }
    jint size[2] = { width, height };
    (*env)->SetIntArrayRegion(env, result, 0, 2, size);
    for (int y = 0; y < height; y++) {
        const uint8_t *p = plane + (size_t) y * stride;
        for (int x = 0; x < width; x++, p += 3) {
            row[x] = (jint) (0xFF000000u | ((uint32_t) p[0] << 16) | ((uint32_t) p[1] << 8) | p[2]);
        }
        (*env)->SetIntArrayRegion(env, result, 2 + (jsize) y * width, width, row);
    }

done:
    free(row);
    if (image != NULL) heif_image_release(image);
    if (handle != NULL) heif_image_handle_release(handle);
    if (ctx != NULL) heif_context_free(ctx);
    free(bytes);
    return result;
}
