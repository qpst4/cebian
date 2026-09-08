#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PP-FormulaNet_plus-M 模型下载与移动端 ONNX 导出脚本

依赖安装要求:
    pip install paddlepaddle paddlex paddle2onnx onnx onnxruntime

执行方式:
    python scripts/export_pp_formulanet_m.py
"""

import os
import sys
import shutil

def export_model():
    print("====================================================")
    print(" 正在开始 PP-FormulaNet_plus-M 移动端模型转换流程")
    print("====================================================")

    try:
        import paddlex
        from paddlex import create_model
    except ImportError:
        print("[错误] 未检测到 paddlex，请先执行命令安装:")
        print("    pip install paddlepaddle paddlex paddle2onnx onnx")
        sys.exit(1)

    model_name = "PP-FormulaNet_plus-M"
    output_dir = os.path.abspath("./model_output/pp_formulanet_plus_m")
    export_infer_dir = os.path.join(output_dir, "inference")
    onnx_dir = os.path.join(output_dir, "onnx")

    os.makedirs(export_infer_dir, exist_ok=True)
    os.makedirs(onnx_dir, exist_ok=True)

    print(f"[1/3] 正在拉取官方预训练模型权重: {model_name}...")
    try:
        model = create_model(model_name)
    except Exception as e:
        print(f"[错误] 下载或加载模型失败: {e}")
        print("请检查网络连接，或前往 https://huggingface.co/PaddlePaddle/ 手动下载。")
        sys.exit(1)

    print(f"[2/3] 正在导出 Paddle 推理模型到: {export_infer_dir}...")
    model.export(export_infer_dir)

    # 提取字典文件 vocab.txt
    vocab_found = False
    for root, dirs, files in os.walk(export_infer_dir):
        for file in files:
            if "dict" in file.lower() or "vocab" in file.lower() or file.endswith(".txt"):
                src_path = os.path.join(root, file)
                dst_path = os.path.join(onnx_dir, "vocab.txt")
                shutil.copyfile(src_path, dst_path)
                print(f"    -> 成功提取字典文件: {dst_path}")
                vocab_found = True
                break
        if vocab_found:
            break

    if not vocab_found:
        print("[警告] 未在导出目录中自动定位到字典文件，请手动将 vocab 放入 onnx 目录。")

    print(f"[3/3] 正在通过 paddle2onnx 转换为 ONNX 格式...")
    pdmodel_path = None
    pdiparams_path = None
    for file in os.listdir(export_infer_dir):
        if file.endswith(".pdmodel"):
            pdmodel_path = os.path.join(export_infer_dir, file)
        elif file.endswith(".pdiparams"):
            pdiparams_path = os.path.join(export_infer_dir, file)

    if pdmodel_path and pdiparams_path:
        onnx_save_path = os.path.join(onnx_dir, "pp_formulanet_plus_m.onnx")
        cmd = (
            f"paddle2onnx "
            f"--model_dir {export_infer_dir} "
            f"--model_filename {os.path.basename(pdmodel_path)} "
            f"--params_filename {os.path.basename(pdiparams_path)} "
            f"--save_file {onnx_save_path} "
            f"--opset_version 14 "
            f"--enable_onnx_checker True"
        )
        print(f"    执行命令: {cmd}")
        ret = os.system(cmd)
        if ret == 0:
            print(f"    -> ONNX 转换成功: {onnx_save_path}")
        else:
            print(f"    [警告] paddle2onnx 自动转换退出码: {ret}")
    else:
        print("[错误] 未找到 .pdmodel 或 .pdiparams 文件，请检查导出目录。")

    print("\n====================================================")
    print(" 转换流程结束！")
    print(f" 最终产物目录: {onnx_dir}")
    print(" 将 onnx 模型和 vocab.txt 复制到 Android 工程的 assets/formula/ 即可。")
    print("====================================================")

if __name__ == "__main__":
    export_model()
