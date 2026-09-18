#!/usr/bin/env python3
"""Build app/src/main/res/values-ar/strings.xml from community APK dump + main key set."""
from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
OUT_STRINGS = ROOT / "app/src/main/res/values-ar/strings.xml"
CONTRIB = Path(r"d:\Downloads\Telegram Desktop\string边栏_1.10.3.apk.xml")
EN_STRINGS = ROOT / "app/src/main/res/values-en/strings.xml"

# Reviewed overrides (community file → corrected Arabic)
FIXES: dict[str, str] = {
    "corner_gesture_settings_desc": (
        "اسحب من الزاوية اليسرى أو اليمنى السفلية لإظهار عجلة إجراءات قطاعية (شعاعية)"
    ),
    "keyevent_desc_keycode_button_a": "زر تأكيد A على يد التحكم",
    "keyevent_desc_keycode_button_b": "زر إلغاء/رجوع B على يد التحكم",
    "keyevent_desc_keycode_button_c": "زر C على يد التحكم ذات الأزرار الستة",
    "keyevent_label_keycode_button_a": "يد التحكم · A",
    "keyevent_label_keycode_button_b": "يد التحكم · B",
    "keyevent_label_keycode_button_c": "يد التحكم · C",
    "preset_se_luna_music_desc": "البحث في Luna Music الرسمي من Douyin",
    "gesture_action_click_passthrough": "تجاوز النقر",
    "accessibility_service_desc": (
        "يجب تفعيله لاستخدام إيماءات الحافة ولوحة الشريط الجانبي، وتنفيذ إيماءات النظام "
        "مثل الرجوع، الشاشة الرئيسية، والمهام المتعددة؛ يمكن استخدامه أيضاً لتجاوز النقر "
        "عند الحافة دون تفويض Shizuku."
    ),
    "permission_accessibility_desc": (
        "يجب تفعيل الشريط الجانبي لاستخدام إيماءات الحافة ولوحة الشريط الجانبي؛ "
        "كما يُستخدم لتنفيذ إيماءات النظام مثل الرجوع، الشاشة الرئيسية، والمهام المتعددة؛ "
        "يمكن استخدامه أيضًا لتجاوز النقر عند الحافة دون تفويض Shizuku."
    ),
    "permission_shizuku_desc": (
        "بعد التفويض، يمكنك إغلاق بطاقات المهام المتعددة، وإغلاق التطبيقات، "
        "وكذلك تنفيذ تجاوز النقر عند الحافة عبر أوامر shell (موصى به)."
    ),
    "keyevent_desc_keycode_button_mode": (
        "زر الدليل (Guide) / وضع Mode في منتصف يد التحكم"
    ),
}

# Keys missing from 1.10.3 community dump (aligned with current main)
MISSING_AR: dict[str, str] = {
    "app_carousel_cancel_both_directions": "إلغاء بالاتجاهين أعلى وأسفل",
    "app_carousel_cancel_distance": "مسافة الإلغاء",
    "app_carousel_separate_cancel_distances": "تعيين مسافات الإلغاء لأعلى وأسفل بشكل منفصل",
    "cd_edge_trigger_bottom": "مشغّل الحافة السفلية %1$d",
    "cd_edge_trigger_left": "مشغّل الحافة اليسرى %1$d",
    "cd_edge_trigger_right": "مشغّل الحافة اليمنى %1$d",
    "cd_edge_trigger_top": "مشغّل الحافة العلوية %1$d",
    "cloud_translate_api_configured": "تم إعداد مفتاح API",
    "cloud_translate_api_missing": "لم يتم إعداد مفتاح API",
    "cloud_translate_api_nav_subtitle": "يُخزَّن لكل مزوّد؛ قد يطابق إعداد OCR لنفس المزوّد",
    "cloud_translate_api_nav_title": "مفتاح API وعنوان Base URL",
    "cloud_translate_api_settings_subtitle": "المفاتيح ونقطة النهاية فقط — بدون نموذج OCR أو مطالبات",
    "cloud_translate_api_settings_title": "%1$s · واجهة الترجمة",
    "cloud_translate_recommended_models": "نماذج النص الموصى بها",
    "cloud_translate_remote_models_count": "%1$d نموذجًا (عرض حتى 80؛ أدخل الأخرى يدويًا)",
    "cloud_translate_remote_models_error_auth": "فشل المصادقة — تحقق من مفتاح API",
    "cloud_translate_remote_models_error_empty": "لم يُرجع الخادم أي نماذج نص",
    "cloud_translate_remote_models_error_generic": "فشل الجلب: %1$s",
    "cloud_translate_remote_models_from_api": "قائمة من Base URL (تم استبعاد نماذج الرؤية/التضمين)",
    "cloud_translate_remote_models_refresh": "تحديث قائمة النماذج",
    "cloud_translate_remote_models_title": "نماذج النص من الخادم",
    "corner_gesture_slot_haptic": "اهتزاز عند تغيير الفتحة",
    "corner_gesture_slot_haptic_desc": "اهتزاز عند انتقال الإصبع إلى فتحة جديدة (مع احترام إعداد الاهتزاز في الصفحة الرئيسية)",
    "diagnostic_log_copy": "نسخ",
    "float_ball_pick_haptic_enabled": "اهتزاز اللوحة",
    "float_ball_pick_haptic_enabled_desc": "اهتزاز خفيف عند النسخ الناجح وعند تحديد الكلمات في وضع النقر على الكلمات",
    "float_ball_pick_panel_style": "نمط لوحة الالتقاط",
    "float_ball_pick_panel_style_desc": "اختر تخطيط عرض نتائج الالتقاط ولقطة الشاشة",
    "float_ball_pick_panel_style_integrated": "بطاقتان متجاورتان",
    "float_ball_pick_panel_style_tab_paged": "تبويبات متعددة الصفحات",
    "float_ball_pick_panel_tab_image": "لقطة الشاشة",
    "float_ball_pick_panel_tab_text": "النص",
    "float_ball_pick_search_grid_state_collapsed": "مطوي دائمًا عند الفتح",
    "float_ball_pick_search_grid_state_desc": "الحالة الابتدائية لمنطقة محركات البحث السفلية عند فتح اللوحة",
    "float_ball_pick_search_grid_state_expanded": "موسّع دائمًا عند الفتح",
    "float_ball_pick_search_grid_state_remember": "تذكر آخر حالة (موصى به)",
    "float_ball_pick_search_grid_state_title": "الحالة الافتراضية لمحركات البحث",
    "float_ball_pick_smart_call_phone": "اتصال",
    "float_ball_pick_smart_copied_toast": "تم النسخ إلى الحافظة",
    "float_ball_pick_smart_copy_code": "نسخ الرمز",
    "float_ball_pick_smart_open_ecommerce": "فتح المتجر",
    "float_ball_pick_smart_open_url": "فتح الرابط",
    "float_ball_pick_smart_track_express": "تتبع الشحنة",
    "gesture_action_require_min_sdk_36": "يتطلب Android 16 (SDK 36) كحد أدنى",
    "image_editor_delay_delete_desc": "عند التفعيل، الحذف بعد مهلة عند النقر على حفظ، والضغط المطول للحفظ الدائم",
    "image_editor_delay_delete_title": "حذف الصورة بعد 5 دقائق",
    "inspire_image_edit_brush_drag_hint": "انقر أو اسحب يمينًا/يسارًا لضبط حجم الفرشاة",
    "inspire_image_edit_clear": "مسح",
    "inspire_image_edit_color_drag_hint": "انقر أو اسحب يمينًا/يسارًا لاختيار اللون",
    "inspire_image_edit_continue": "متابعة التحرير",
    "inspire_image_edit_hint_crop": "اسحب لتحديد المنطقة المراد الاحتفاظ بها؛ سيتم قص ما خارج الإطار.",
    "inspire_image_edit_hint_doodle": "اسحب لرسم خط متصل.",
    "inspire_image_edit_hint_eraser": "اسحب لمحو ما رسمته.",
    "inspire_image_edit_hint_mosaic": "اسحب لتمويه المناطق المطلوبة.",
    "inspire_image_edit_hint_number": "انقر الصورة لوضع أرقام متزايدة؛ يمكن السحب والتكبير.",
    "inspire_image_edit_hint_shape": "اسحب لإنشاء شكل؛ انقر شكلًا موجودًا لتحريكه أو تغيير حجمه من الزاوية.",
    "inspire_image_edit_hint_text": "أضف نصًا من اللوحة أدناه؛ انقر مرتين على نص موجود للتحرير.",
    "inspire_image_edit_menu_save_auto_delete": "حفظ مؤقت (حذف خلال 5 دقائق)",
    "inspire_image_edit_menu_save_persist": "حفظ في الألبوم",
    "inspire_image_edit_number_style_drag_hint": "انقر لاختيار نمط الترقيم",
    "inspire_image_edit_reset_view": "إعادة ضبط العرض",
    "inspire_image_edit_save_message": "الصورة ما زالت في ذاكرة التخزين المؤقت فقط. اختر الحفظ الدائم أو الحفظ في الألبوم مع الحذف بعد 5 دقائق.",
    "inspire_image_edit_save_title": "حفظ نتيجة التحرير",
    "inspire_image_edit_shape_double_arrow": "سهم مزدوج",
    "inspire_image_edit_shape_drag_hint": "انقر أو اسحب يمينًا/يسارًا لاختيار الشكل",
    "inspire_image_edit_size_label": "حجم التصدير",
    "inspire_image_edit_size_value": "%1$d%%",
    "inspire_image_edit_title": "تحرير الصورة",
    "launch_policy_always_free_window_desc": "فتح في نافذة حرة عند رفع الإصبع",
    "launch_policy_always_fullscreen_desc": "فتح ملء الشاشة عند رفع الإصبع",
    "launch_policy_free_window_long_press_fullscreen_desc": "نافذة حرة افتراضيًا؛ اضغط مطولًا على الأيقونة ثم ارفع لفتح ملء الشاشة",
    "launch_policy_fullscreen_long_press_free_window_desc": "ملء الشاشة افتراضيًا؛ اضغط مطولًا على الأيقونة ثم ارفع لفتح نافذة حرة",
    "pick_result_action_image_search": "بحث بالصورة",
    "pick_result_action_more": "المزيد",
    "pick_result_action_save_image": "حفظ الصورة",
    "pick_result_action_share_image": "مشاركة الصورة",
    "pick_result_copied": "تم النسخ",
    "pick_result_image_share_last_used": "الأخيرة",
    "pick_result_image_share_long_press_hint": "اسحب للاختيار وارفع للمشاركة؛ اسحب جانبيًا للإلغاء",
    "pick_result_open_link": "فتح الرابط",
    "pick_result_pin": "تثبيت على الشاشة",
    "pick_result_share": "مشاركة",
    "pick_result_stash": "إخفاء مؤقت",
    "screen_capture_permission_required": "يلزم إذن التقاط الشاشة للقطع الجزئي",
    "screen_search_direction_down": "لأسفل",
    "screen_search_direction_up": "لأعلى",
    "screen_search_scroll_direction": "اتجاه التمرير",
    "search_engine_settings_hint_tip": "انقر أيقونة للتحرير أو الإدارة؛ اضغط مطولًا للسحب وإعادة الترتيب بين الصفحات",
    "search_panel_app_quick_action_details": "التفاصيل",
    "search_panel_app_quick_action_free_window": "نافذة صغيرة",
    "search_panel_app_quick_action_freeze": "تجميد",
    "search_panel_app_quick_action_share": "مشاركة",
    "search_panel_enter_action_first_candidate": "تشغيل أول نتيجة",
    "search_panel_enter_action_search_engine": "محرك البحث (افتراضي)",
    "search_panel_enter_action_title": "إجراء مفتاح Enter",
    "search_panel_presentation_layout_entry_desc": "نمط العرض، موضع الشريط، الخلفية، وسلوك التشغيل",
    "search_panel_settings_section_local_search": "بحث المحتوى المحلي",
    "search_panel_settings_section_smart_candidates": "اقتراحات ذكية وفورية",
    "settings_backup_preview_cloud_config": "يتضمن مفاتيح OCR/الترجمة السحابية وعناوين API والمطالبات",
    "temp_dnd_minutes_invalid": "أدخل قيمة أكبر من 0",
    "temp_dnd_notification_channel_desc": "يعرض الوقت المتبقي لوضع عدم الإزعاج المؤقت",
    "temp_dnd_notification_channel_name": "العد التنازلي لعدم الإزعاج",
    "temp_dnd_notification_redefine_action": "تغيير المدة",
    "temp_dnd_notification_redefine_hint": "دقائق",
    "temp_dnd_notification_stop_action": "إيقاف الآن",
    "temp_dnd_notification_text": "المدة الإجمالية: %1$d دقيقة",
    "temp_dnd_notification_title": "عدم الإزعاج المؤقت مفعّل",
    "temp_dnd_policy_access_required": "فعّل إذن التحكم في عدم الإزعاج أولًا",
    "widget_panel_page_indicator": "الصفحة %1$d / %2$d",
}


def load_strings(path: Path) -> dict[str, str]:
    tree = ET.parse(path)
    root = tree.getroot()
    out: dict[str, str] = {}
    for el in root.findall("string"):
        name = el.attrib["name"]
        parts: list[str] = []
        if el.text:
            parts.append(el.text)
        for child in el:
            parts.append(ET.tostring(child, encoding="unicode", method="xml"))
            if child.tail:
                parts.append(child.tail)
        out[name] = "".join(parts)
    return out


def ordered_source_names(path: Path) -> list[str]:
    tree = ET.parse(path)
    root = tree.getroot()
    return [el.attrib["name"] for el in root.findall("string")]


def escape_android_xml(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', '\\"')
    )


def main() -> None:
    if not CONTRIB.is_file():
        raise SystemExit(f"Community file not found: {CONTRIB}")

    order = ordered_source_names(SRC_STRINGS)
    src = load_strings(SRC_STRINGS)
    contrib = load_strings(CONTRIB)
    en = load_strings(EN_STRINGS)

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<resources>",
    ]
    missing_fill = 0
    from_contrib = 0
    from_fix = 0

    for name in order:
        if name in FIXES:
            value = FIXES[name]
            from_fix += 1
        elif name in contrib and not name.startswith("abc_"):
            value = contrib[name]
            from_contrib += 1
        elif name in MISSING_AR:
            value = MISSING_AR[name]
            missing_fill += 1
        elif name in en:
            value = en[name]
            missing_fill += 1
        else:
            value = src[name]
            missing_fill += 1

        lines.append(f'    <string name="{name}">{escape_android_xml(value)}</string>')

    lines.append("</resources>")
    lines.append("")

    OUT_STRINGS.parent.mkdir(parents=True, exist_ok=True)
    OUT_STRINGS.write_text("\n".join(lines), encoding="utf-8", newline="\n")

    print(f"Wrote {len(order)} strings to {OUT_STRINGS}")
    print(f"  from community: {from_contrib}, fixes: {from_fix}, new/extra fill: {missing_fill}")
    if set(MISSING_AR) - set(order):
        print("WARN: MISSING_AR has unknown keys:", set(MISSING_AR) - set(order))
    gap = set(order) - set(contrib) - set(MISSING_AR) - set(FIXES)
    if gap:
        print(f"WARN: {len(gap)} keys neither in contrib nor MISSING_AR (used en/zh fallback)")


if __name__ == "__main__":
    main()
