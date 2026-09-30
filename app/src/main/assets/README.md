Place your AI models in this directory for hardware-accelerated processing:

1. zero_dce_plus.tflite - Stage 1 Low-Light Recovery / Denoising
2. fast_srgan.tflite / real_esrgan_compact_4x.tflite / real_esrgan_compact_4x.onnx - Stage 2 4x Super Resolution
3. gfpgan.onnx / codeformer.onnx - Stage 3 Face Restoration

Note: If any model file is omitted, UltraEnhance automatically switches to its high-quality native Android ColorMatrix and bilinear filter fallback engine without crashing!
