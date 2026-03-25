package com.driot.bookplayer.helpers;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.librivox.LanguageMapper;

import java.util.Locale;

public final class FlagHelper {
    private FlagHelper() {}

    // If you add a drawable like no_flag.png, change this to R.drawable.no_flag
    private static final @DrawableRes int NO_FLAG = R.drawable.no_flag;


    public static int getFlagResId(Context context, String code2, String codeType) {
        if (code2 == null)
            return 0;
        if (codeType.equals("language")) {
            String countryCode = LanguageHelper.getCountryForLanguage(code2);
            if (countryCode == null) {
                return LanguageMapper.getFlagFromName(code2); //fallback
            }
            return context.getResources().getIdentifier("flag_" + countryCode.toLowerCase(), "drawable",
                    context.getPackageName());
        } else if (codeType.equals("country")) {
            return context.getResources().getIdentifier("flag_" + code2.toLowerCase(), "drawable",
                    context.getPackageName());
        } else {
            return 0;
        }
    }

    /** Country code → drawable (ISO-3166 alpha-2; case-insensitive). */
    public static @DrawableRes int getFlagResIdForCountry(@Nullable String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) return NO_FLAG;
        switch (countryCode.toLowerCase(Locale.ROOT)) {
            case "ad": return R.drawable.flag_ad;
            case "ae": return R.drawable.flag_ae;
            case "af": return R.drawable.flag_af;
            case "ag": return R.drawable.flag_ag;
            case "ai": return R.drawable.flag_ai;
            case "al": return R.drawable.flag_al;
            case "am": return R.drawable.flag_am;
            case "an": return R.drawable.flag_an; // (obsolete; Netherlands Antilles)
            case "ao": return R.drawable.flag_ao;
            case "aq": return R.drawable.flag_aq;
            case "ar": return R.drawable.flag_ar;
            case "as": return R.drawable.flag_as;
            case "at": return R.drawable.flag_at;
            case "au": return R.drawable.flag_au;
            case "aw": return R.drawable.flag_aw;
            case "az": return R.drawable.flag_az;
            case "ba": return R.drawable.flag_ba;
            case "bb": return R.drawable.flag_bb;
            case "bd": return R.drawable.flag_bd;
            case "be": return R.drawable.flag_be;
            case "bf": return R.drawable.flag_bf;
            case "bg": return R.drawable.flag_bg;
            case "bh": return R.drawable.flag_bh;
            case "bi": return R.drawable.flag_bi;
            case "bj": return R.drawable.flag_bj;
            case "bm": return R.drawable.flag_bm;
            case "bn": return R.drawable.flag_bn;
            case "bo": return R.drawable.flag_bo;
            case "br": return R.drawable.flag_br;
            case "bs": return R.drawable.flag_bs;
            case "bt": return R.drawable.flag_bt;
            case "bv": return R.drawable.flag_bv;
            case "bw": return R.drawable.flag_bw;
            case "by": return R.drawable.flag_by;
            case "bz": return R.drawable.flag_bz;
            case "ca": return R.drawable.flag_ca;
            case "cc": return R.drawable.flag_cc;
            case "cd": return R.drawable.flag_cd;
            case "cf": return R.drawable.flag_cf;
            case "cg": return R.drawable.flag_cg;
            case "ch": return R.drawable.flag_ch;
            case "ci": return R.drawable.flag_ci;
            case "ck": return R.drawable.flag_ck;
            case "cl": return R.drawable.flag_cl;
            case "cm": return R.drawable.flag_cm;
            case "cn": return R.drawable.flag_cn;
            case "co": return R.drawable.flag_co;
            case "cr": return R.drawable.flag_cr;
            case "cu": return R.drawable.flag_cu;
            case "cv": return R.drawable.flag_cv;
            case "cx": return R.drawable.flag_cx;
            case "cy": return R.drawable.flag_cy;
            case "cz": return R.drawable.flag_cz;
            case "de": return R.drawable.flag_de;
            case "dj": return R.drawable.flag_dj;
            case "dk": return R.drawable.flag_dk;
            case "dm": return R.drawable.flag_dm;
            case "do": return R.drawable.flag_do;
            case "dz": return R.drawable.flag_dz;
            case "ec": return R.drawable.flag_ec;
            case "ee": return R.drawable.flag_ee;
            case "eg": return R.drawable.flag_eg;
            case "eh": return R.drawable.flag_eh;
            case "er": return R.drawable.flag_er;
            case "es": return R.drawable.flag_es;
            case "et": return R.drawable.flag_et;
            case "fi": return R.drawable.flag_fi;
            case "fj": return R.drawable.flag_fj;
            case "fk": return R.drawable.flag_fk;
            case "fm": return R.drawable.flag_fm;
            case "fo": return R.drawable.flag_fo;
            case "fr": return R.drawable.flag_fr;
            case "ga": return R.drawable.flag_ga;
            case "gd": return R.drawable.flag_gd;
            case "ge": return R.drawable.flag_ge;
            case "gf": return R.drawable.flag_gf;
            case "gh": return R.drawable.flag_gh;
            case "gi": return R.drawable.flag_gi;
            case "gl": return R.drawable.flag_gl;
            case "gm": return R.drawable.flag_gm;
            case "gn": return R.drawable.flag_gn;
            case "gp": return R.drawable.flag_gp;
            case "gq": return R.drawable.flag_gq;
            case "gr": return R.drawable.flag_gr;
            case "gs": return R.drawable.flag_gs;
            case "gt": return R.drawable.flag_gt;
            case "gu": return R.drawable.flag_gu;
            case "gw": return R.drawable.flag_gw;
            case "gy": return R.drawable.flag_gy;
            case "hk": return R.drawable.flag_hk;
            case "hm": return R.drawable.flag_hm;
            case "hn": return R.drawable.flag_hn;
            case "hr": return R.drawable.flag_hr;
            case "ht": return R.drawable.flag_ht;
            case "hu": return R.drawable.flag_hu;
            case "id": return R.drawable.flag_id;
            case "ie": return R.drawable.flag_ie;
            case "il": return R.drawable.flag_il;
            case "in": return R.drawable.flag_in;
            case "io": return R.drawable.flag_io;
            case "iq": return R.drawable.flag_iq;
            case "ir": return R.drawable.flag_ir;
            case "is": return R.drawable.flag_is;
            case "it": return R.drawable.flag_it;
            case "jm": return R.drawable.flag_jm;
            case "jo": return R.drawable.flag_jo;
            case "jp": return R.drawable.flag_jp;
            case "ke": return R.drawable.flag_ke;
            case "kg": return R.drawable.flag_kg;
            case "kh": return R.drawable.flag_kh;
            case "ki": return R.drawable.flag_ki;
            case "km": return R.drawable.flag_km;
            case "kn": return R.drawable.flag_kn;
            case "kp": return R.drawable.flag_kp;
            case "kr": return R.drawable.flag_kr;
            case "kw": return R.drawable.flag_kw;
            case "ky": return R.drawable.flag_ky;
            case "kz": return R.drawable.flag_kz;
            case "la": return R.drawable.flag_la;
            case "lb": return R.drawable.flag_lb;
            case "lc": return R.drawable.flag_lc;
            case "li": return R.drawable.flag_li;
            case "lk": return R.drawable.flag_lk;
            case "lr": return R.drawable.flag_lr;
            case "ls": return R.drawable.flag_ls;
            case "lt": return R.drawable.flag_lt;
            case "lu": return R.drawable.flag_lu;
            case "lv": return R.drawable.flag_lv;
            case "ly": return R.drawable.flag_ly;
            case "ma": return R.drawable.flag_ma;
            case "mc": return R.drawable.flag_mc;
            case "md": return R.drawable.flag_md;
            case "me": return R.drawable.flag_me;
            case "mg": return R.drawable.flag_mg;
            case "mh": return R.drawable.flag_mh;
            case "mk": return R.drawable.flag_mk;
            case "ml": return R.drawable.flag_ml;
            case "mm": return R.drawable.flag_mm;
            case "mn": return R.drawable.flag_mn;
            case "mo": return R.drawable.flag_mo;
            case "mp": return R.drawable.flag_mp;
            case "mq": return R.drawable.flag_mq;
            case "mr": return R.drawable.flag_mr;
            case "ms": return R.drawable.flag_ms;
            case "mt": return R.drawable.flag_mt;
            case "mu": return R.drawable.flag_mu;
            case "mv": return R.drawable.flag_mv;
            case "mw": return R.drawable.flag_mw;
            case "mx": return R.drawable.flag_mx;
            case "my": return R.drawable.flag_my;
            case "mz": return R.drawable.flag_mz;
            case "na": return R.drawable.flag_na;
            case "nc": return R.drawable.flag_nc;
            case "ne": return R.drawable.flag_ne;
            case "nf": return R.drawable.flag_nf;
            case "ng": return R.drawable.flag_ng;
            case "ni": return R.drawable.flag_ni;
            case "nl": return R.drawable.flag_nl;
            case "no": return R.drawable.flag_no;
            case "np": return R.drawable.flag_np;
            case "nr": return R.drawable.flag_nr;
            case "nu": return R.drawable.flag_nu;
            case "nz": return R.drawable.flag_nz;
            case "om": return R.drawable.flag_om;
            case "pa": return R.drawable.flag_pa;
            case "pe": return R.drawable.flag_pe;
            case "pf": return R.drawable.flag_pf;
            case "pg": return R.drawable.flag_pg;
            case "ph": return R.drawable.flag_ph;
            case "pk": return R.drawable.flag_pk;
            case "pl": return R.drawable.flag_pl;
            case "pm": return R.drawable.flag_pm;
            case "pn": return R.drawable.flag_pn;
            case "pr": return R.drawable.flag_pr;
            case "pt": return R.drawable.flag_pt;
            case "pw": return R.drawable.flag_pw;
            case "py": return R.drawable.flag_py;
            case "qa": return R.drawable.flag_qa;
            case "re": return R.drawable.flag_re;
            case "ro": return R.drawable.flag_ro;
            case "rs": return R.drawable.flag_rs;
            case "ru": return R.drawable.flag_ru;
            case "rw": return R.drawable.flag_rw;
            case "sa": return R.drawable.flag_sa;
            case "sb": return R.drawable.flag_sb;
            case "sc": return R.drawable.flag_sc;
            case "sd": return R.drawable.flag_sd;
            case "se": return R.drawable.flag_se;
            case "sg": return R.drawable.flag_sg;
            case "sh": return R.drawable.flag_sh;
            case "si": return R.drawable.flag_si;
            case "sj": return R.drawable.flag_sj;
            case "sk": return R.drawable.flag_sk;
            case "sl": return R.drawable.flag_sl;
            case "sm": return R.drawable.flag_sm;
            case "sn": return R.drawable.flag_sn;
            case "so": return R.drawable.flag_so;
            case "sr": return R.drawable.flag_sr;
            case "ss": return R.drawable.flag_ss;
            case "st": return R.drawable.flag_st;
            case "sv": return R.drawable.flag_sv;
            case "sy": return R.drawable.flag_sy;
            case "sz": return R.drawable.flag_sz;
            case "tc": return R.drawable.flag_tc;
            case "td": return R.drawable.flag_td;
            case "tf": return R.drawable.flag_tf;
            case "tg": return R.drawable.flag_tg;
            case "th": return R.drawable.flag_th;
            case "tj": return R.drawable.flag_tj;
            case "tk": return R.drawable.flag_tk;
            case "tl": return R.drawable.flag_tl; // East Timor (new code)
            case "tm": return R.drawable.flag_tm;
            case "tn": return R.drawable.flag_tn;
            case "to": return R.drawable.flag_to;
            case "tp": return R.drawable.flag_tp; // (obsolete old East Timor)
            case "tr": return R.drawable.flag_tr;
            case "tt": return R.drawable.flag_tt;
            case "tv": return R.drawable.flag_tv;
            case "tw": return R.drawable.flag_tw;
            case "ty": return R.drawable.flag_ty;
            case "tz": return R.drawable.flag_tz;
            case "ua": return R.drawable.flag_ua;
            case "ug": return R.drawable.flag_ug;
            case "gb": return R.drawable.flag_uk;
            case "uk": return R.drawable.flag_uk;
            case "um": return R.drawable.flag_um;
            case "us": return R.drawable.flag_us;
            case "uy": return R.drawable.flag_uy;
            case "uz": return R.drawable.flag_uz;
            case "va": return R.drawable.flag_va;
            case "vc": return R.drawable.flag_vc;
            case "ve": return R.drawable.flag_ve;
            case "vg": return R.drawable.flag_vg;
            case "vi": return R.drawable.flag_vi;
            case "vn": return R.drawable.flag_vn;
            case "vu": return R.drawable.flag_vu;
            case "wf": return R.drawable.flag_wf;
            case "ws": return R.drawable.flag_ws;
            case "ye": return R.drawable.flag_ye;
            case "za": return R.drawable.flag_za;
            case "zm": return R.drawable.flag_zm;
            case "zr": return R.drawable.flag_zr; // (obsolete; Zaire)
            case "zw": return R.drawable.flag_zw;

            case "xa": return R.drawable.flag_sa;
            default: return NO_FLAG;
        }
    }

    /** Convenience: Locale → drawable (uses locale.country). */
    public static @DrawableRes int getFlagResId(@Nullable Locale locale) {
        return (locale == null) ? NO_FLAG : getFlagResIdForCountry(locale.getCountry());
    }

    /** Language code → representative flag (ISO-639; you can tweak choices). */
    public static @DrawableRes int getFlagResIdForLanguage(@Nullable String langCode) {
        if (langCode == null || langCode.isEmpty()) return NO_FLAG;
        switch (langCode.toLowerCase(Locale.ROOT)) {
            case "fr": case "fra": case "fre": return R.drawable.flag_fr;
            case "en": case "eng":              return R.drawable.flag_uk;  // or flag_us
            case "es": case "spa":              return R.drawable.flag_es;
            case "pt": case "por":              return R.drawable.flag_pt;
            case "zh": case "zho": case "chi":  return R.drawable.flag_cn;
            case "ja": case "jpn":              return R.drawable.flag_jp;
            case "de": case "deu": case "ger":  return R.drawable.flag_de;
            case "it": case "ita":              return R.drawable.flag_it;
            case "ru": case "rus":              return R.drawable.flag_ru;
            case "ar": case "ara":              return R.drawable.flag_sa;
            case "nl": case "nld": case "dut":  return R.drawable.flag_nl;
            case "sv": case "swe":              return R.drawable.flag_se;
            case "no": case "nor":              return R.drawable.flag_no;
            case "da": case "dan":              return R.drawable.flag_dk;
            case "fi": case "fin":              return R.drawable.flag_fi;
            case "pl": case "pol":              return R.drawable.flag_pl;
            case "cs": case "ces": case "cze":  return R.drawable.flag_cz;
            case "tr": case "tur":              return R.drawable.flag_tr;
            case "el": case "ell": case "gre":  return R.drawable.flag_gr;
            case "he": case "heb":              return R.drawable.flag_il;
            case "ko": case "kor":              return R.drawable.flag_kr;
            case "hi": case "hin":              return R.drawable.flag_in;
            case "id": case "ind":              return R.drawable.flag_id;
            case "ms": case "msa": case "may":  return R.drawable.flag_my;
            case "vi": case "vie":              return R.drawable.flag_vn;
            case "th": case "tha":              return R.drawable.flag_th;
            case "uk": case "ukr":              return R.drawable.flag_ua;
            case "ro": case "ron": case "rum":  return R.drawable.flag_ro;
            case "bg": case "bul":              return R.drawable.flag_bg;
            case "hu": case "hun":              return R.drawable.flag_hu;
            case "sr": case "srp":              return R.drawable.flag_rs;
            case "sk": case "slk": case "slo":  return R.drawable.flag_sk;
            case "sl": case "slv":              return R.drawable.flag_si;
            case "hr": case "hrv":              return R.drawable.flag_hr;
            case "lt": case "lit":              return R.drawable.flag_lt;
            case "lv": case "lav":              return R.drawable.flag_lv;
            case "et": case "est":              return R.drawable.flag_ee;
            case "ga": case "gle":              return R.drawable.flag_ie;
            case "is": case "isl":              return R.drawable.flag_is;
            case "mt": case "mlt":              return R.drawable.flag_mt;
            case "sq": case "sqi": case "alb":  return R.drawable.flag_al;
            case "mk": case "mkd": case "mac":  return R.drawable.flag_mk;
            case "bs": case "bos":              return R.drawable.flag_ba;
            case "fa": case "fas": case "per": case "pes": return R.drawable.flag_ir;
            case "ur": case "urd":              return R.drawable.flag_pk;
            case "bn": case "ben":              return R.drawable.flag_bd;
            case "ne": case "nep":              return R.drawable.flag_np;
            case "af": case "afr":              return R.drawable.flag_za;
            case "sw": case "swa":              return R.drawable.flag_tz;
            case "am": case "amh":              return R.drawable.flag_et;
            case "hy": case "hye": case "arm":  return R.drawable.flag_am;
            case "ka": case "kat": case "geo":  return R.drawable.flag_ge;

            case "fil": return R.drawable.flag_ph;
            case "jv": return R.drawable.flag_id;
            case "nb": return R.drawable.flag_no;
            case "si": return R.drawable.flag_lk;

            default: return NO_FLAG;
        }
    }
}
