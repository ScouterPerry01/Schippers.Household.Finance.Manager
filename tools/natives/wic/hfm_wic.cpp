// RANN's Roost: HEIC photos on Windows (CAP-03, ADR 0004).
//
// A small JNI library that asks Windows Imaging Component to decode a picture. It contains no
// codec: HEIC is read only when the user has installed Microsoft's HEIF Image Extensions and HEVC
// Video Extensions, which carry the HEVC licence. The picture is decoded from memory, never from a
// temporary file, turned upright from its orientation, reduced, and returned as ARGB pixels.
// Called by ca.schippers.hfm.ocr.desktop.WicNative.
//
// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <windows.h>
#include <wincodec.h>
#include <propvarutil.h>
#include <cstdio>
#include <vector>

namespace {

template <typename T>
struct Com {
    T *p = nullptr;
    ~Com() { if (p) p->Release(); }
    T **out() { return &p; }
    T *operator->() const { return p; }
};

void throwIo(JNIEnv *env, const char *what, HRESULT hr) {
    char message[160];
    std::snprintf(message, sizeof message, "%s (0x%08lX)", what, static_cast<unsigned long>(hr));
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) env->ThrowNew(cls, message);
}

// The rotation that turns a picture with EXIF orientation 1..8 upright.
WICBitmapTransformOptions transformFor(USHORT orientation) {
    switch (orientation) {
        case 2: return WICBitmapTransformFlipHorizontal;
        case 3: return WICBitmapTransformRotate180;
        case 4: return WICBitmapTransformFlipVertical;
        case 5: return static_cast<WICBitmapTransformOptions>(WICBitmapTransformRotate90 | WICBitmapTransformFlipHorizontal);
        case 6: return WICBitmapTransformRotate90;
        case 7: return static_cast<WICBitmapTransformOptions>(WICBitmapTransformRotate270 | WICBitmapTransformFlipHorizontal);
        case 8: return WICBitmapTransformRotate270;
        default: return WICBitmapTransformRotate0;
    }
}

jintArray decode(JNIEnv *env, jbyteArray data, jint maxPixels, jint maxSide) {
    jsize length = env->GetArrayLength(data);
    std::vector<BYTE> bytes(length > 0 ? length : 1);
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));

    Com<IWICImagingFactory> factory;
    Com<IWICStream> stream;
    Com<IWICBitmapDecoder> decoder;
    Com<IWICBitmapFrameDecode> frame;
    HRESULT hr = CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(factory.out()));
    if (SUCCEEDED(hr)) hr = factory->CreateStream(stream.out());
    if (SUCCEEDED(hr)) hr = stream->InitializeFromMemory(bytes.data(), static_cast<DWORD>(length));
    if (SUCCEEDED(hr)) hr = factory->CreateDecoderFromStream(stream.p, nullptr, WICDecodeMetadataCacheOnLoad, decoder.out());
    if (FAILED(hr)) {
        throwIo(env, "no decoder for this picture", hr);
        return nullptr;
    }
    hr = decoder->GetFrame(0, frame.out());
    UINT w = 0, h = 0;
    if (SUCCEEDED(hr)) hr = frame->GetSize(&w, &h);
    if (FAILED(hr) || w == 0 || h == 0) {
        throwIo(env, "the picture cannot be read", hr);
        return nullptr;
    }
    if (static_cast<long long>(w) * h > maxPixels) {
        throwIo(env, "image too large", E_FAIL);
        return nullptr;
    }

    // Orientation, as Windows Photos uses it.
    USHORT orientation = 1;
    {
        Com<IWICMetadataQueryReader> query;
        if (SUCCEEDED(frame->GetMetadataQueryReader(query.out()))) {
            PROPVARIANT value;
            PropVariantInit(&value);
            if (SUCCEEDED(query->GetMetadataByName(L"System.Photo.Orientation", &value)) && value.vt == VT_UI2) orientation = value.uiVal;
            PropVariantClear(&value);
        }
    }
    IWICBitmapSource *source = frame.p;
    Com<IWICBitmapFlipRotator> rotator;
    WICBitmapTransformOptions transform = transformFor(orientation);
    if (transform != WICBitmapTransformRotate0 && SUCCEEDED(factory->CreateBitmapFlipRotator(rotator.out())) && SUCCEEDED(rotator->Initialize(source, transform))) {
        source = rotator.p;
        source->GetSize(&w, &h);
    }

    Com<IWICBitmapScaler> scaler;
    UINT longSide = w > h ? w : h;
    if (maxSide > 0 && longSide > static_cast<UINT>(maxSide)) {
        UINT nw = static_cast<UINT>(static_cast<unsigned long long>(w) * maxSide / longSide);
        UINT nh = static_cast<UINT>(static_cast<unsigned long long>(h) * maxSide / longSide);
        if (nw == 0) nw = 1;
        if (nh == 0) nh = 1;
        if (SUCCEEDED(factory->CreateBitmapScaler(scaler.out())) && SUCCEEDED(scaler->Initialize(source, nw, nh, WICBitmapInterpolationModeFant))) {
            source = scaler.p;
            w = nw;
            h = nh;
        }
    }

    Com<IWICFormatConverter> converter;
    hr = factory->CreateFormatConverter(converter.out());
    if (SUCCEEDED(hr)) hr = converter->Initialize(source, GUID_WICPixelFormat32bppBGRA, WICBitmapDitherTypeNone, nullptr, 0.0, WICBitmapPaletteTypeCustom);
    std::vector<UINT32> pixels(static_cast<size_t>(w) * h);
    if (SUCCEEDED(hr)) hr = converter->CopyPixels(nullptr, w * 4, static_cast<UINT>(pixels.size() * 4), reinterpret_cast<BYTE *>(pixels.data()));
    if (FAILED(hr)) {
        // With HEIF Image Extensions but no HEVC Video Extensions, decoding stops here.
        throwIo(env, "the picture cannot be decoded", hr);
        return nullptr;
    }
    // BGRA bytes read as a little-endian 32-bit value are 0xAARRGGBB; the photo is opaque.
    for (auto &p : pixels) p |= 0xFF000000u;
    jintArray result = env->NewIntArray(static_cast<jsize>(2 + pixels.size()));
    if (result) {
        jint size[2] = {static_cast<jint>(w), static_cast<jint>(h)};
        env->SetIntArrayRegion(result, 0, 2, size);
        env->SetIntArrayRegion(result, 2, static_cast<jsize>(pixels.size()), reinterpret_cast<const jint *>(pixels.data()));
    }
    return result;
}

}  // namespace

extern "C" {

// Returns [width, height, pixel...] with each pixel as 0xFFRRGGBB, or throws IOException.
JNIEXPORT jintArray JNICALL Java_ca_schippers_hfm_ocr_desktop_WicNative_decode(JNIEnv *env, jclass, jbyteArray data, jint maxPixels, jint maxSide) {
    // RPC_E_CHANGED_MODE means COM is already set up on this thread, and usable as it is.
    bool uninit = SUCCEEDED(CoInitializeEx(nullptr, COINIT_MULTITHREADED));
    jintArray result = decode(env, data, maxPixels, maxSide);
    if (uninit) CoUninitialize();
    return result;
}

}  // extern "C"
