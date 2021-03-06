/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network.telephony;

import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellInfoTdscdma;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.TeleUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Add static Utility functions to get information from the CellInfo object.
 * TODO: Modify {@link CellInfo} for simplify those functions
 */
public final class CellInfoUtil {
    private static final String TAG = "CellInfoUtil";

    // UNISOC: CellInfo types
    private static final int CELL_INFO_TYPE_GSM = CellInfo.TYPE_GSM;
    private static final int CELL_INFO_TYPE_WCDMA = CellInfo.TYPE_WCDMA;
    private static final int CELL_INFO_TYPE_LTE = CellInfo.TYPE_LTE;
    private static final int CELL_INFO_TYPE_TDSCDMA = CellInfo.TYPE_TDSCDMA;

    private CellInfoUtil() {
    }

    /**
     * Wrap a CellIdentity into a CellInfo.
     */
    public static CellInfo wrapCellInfoWithCellIdentity(CellIdentity cellIdentity) {
        if (cellIdentity instanceof CellIdentityLte) {
            CellInfoLte cellInfo = new CellInfoLte();
            cellInfo.setCellIdentity((CellIdentityLte) cellIdentity);
            return cellInfo;
        } else if (cellIdentity instanceof CellIdentityCdma) {
            CellInfoCdma cellInfo = new CellInfoCdma();
            cellInfo.setCellIdentity((CellIdentityCdma) cellIdentity);
            return cellInfo;
        }  else if (cellIdentity instanceof CellIdentityWcdma) {
            CellInfoWcdma cellInfo = new CellInfoWcdma();
            cellInfo.setCellIdentity((CellIdentityWcdma) cellIdentity);
            return cellInfo;
        } else if (cellIdentity instanceof CellIdentityGsm) {
            CellInfoGsm cellInfo = new CellInfoGsm();
            cellInfo.setCellIdentity((CellIdentityGsm) cellIdentity);
            return cellInfo;
        } else if (cellIdentity instanceof CellIdentityTdscdma) {
            CellInfoTdscdma cellInfo = new CellInfoTdscdma();
            cellInfo.setCellIdentity((CellIdentityTdscdma) cellIdentity);
            return cellInfo;
        } else {
            Log.e(TAG, "Invalid CellInfo type");
            return null;
        }
    }

    /**
     * Returns the title of the network obtained in the manual search.
     *
     * @param cellInfo contains the information of the network.
     * @return Long Name if not null/empty, otherwise Short Name if not null/empty,
     * else MCCMNC string.
     */
    public static String getNetworkTitle(CellInfo cellInfo) {
        OperatorInfo oi = getOperatorInfoFromCellInfo(cellInfo);

        if (!TextUtils.isEmpty(oi.getOperatorAlphaLong())) {
//            return oi.getOperatorAlphaLong();
            return TeleUtils.translateOperatorName(oi.getOperatorNumeric(), oi.getOperatorAlphaLong());
        } else if (!TextUtils.isEmpty(oi.getOperatorAlphaShort())) {
//            return oi.getOperatorAlphaShort();
            return TeleUtils.translateOperatorName(oi.getOperatorNumeric(), oi.getOperatorAlphaShort());
        } else {
            BidiFormatter bidiFormatter = BidiFormatter.getInstance();
            return bidiFormatter.unicodeWrap(oi.getOperatorNumeric(), TextDirectionHeuristics.LTR);
        }
    }

    /**
     * Wrap a cell info into an operator info.
     */
    public static OperatorInfo getOperatorInfoFromCellInfo(CellInfo cellInfo) {
        OperatorInfo oi;
        if (cellInfo instanceof CellInfoLte) {
            CellInfoLte lte = (CellInfoLte) cellInfo;
            oi = new OperatorInfo(
                    (String) lte.getCellIdentity().getOperatorAlphaLong(),
                    (String) lte.getCellIdentity().getOperatorAlphaShort(),
                    lte.getCellIdentity().getMobileNetworkOperator());
        } else if (cellInfo instanceof CellInfoWcdma) {
            CellInfoWcdma wcdma = (CellInfoWcdma) cellInfo;
            oi = new OperatorInfo(
                    (String) wcdma.getCellIdentity().getOperatorAlphaLong(),
                    (String) wcdma.getCellIdentity().getOperatorAlphaShort(),
                    wcdma.getCellIdentity().getMobileNetworkOperator());
        } else if (cellInfo instanceof CellInfoGsm) {
            CellInfoGsm gsm = (CellInfoGsm) cellInfo;
            oi = new OperatorInfo(
                    (String) gsm.getCellIdentity().getOperatorAlphaLong(),
                    (String) gsm.getCellIdentity().getOperatorAlphaShort(),
                    gsm.getCellIdentity().getMobileNetworkOperator());
        } else if (cellInfo instanceof CellInfoCdma) {
            CellInfoCdma cdma = (CellInfoCdma) cellInfo;
            oi = new OperatorInfo(
                    (String) cdma.getCellIdentity().getOperatorAlphaLong(),
                    (String) cdma.getCellIdentity().getOperatorAlphaShort(),
                    "" /* operator numeric */);
        } else {
            Log.e(TAG, "Invalid CellInfo type");
            oi = new OperatorInfo("", "", "");
        }
        return oi;
    }

    /** UNISOC: Wrap a cell info into an operator info, which contains alpha long name and rat @{ */
    public static OperatorInfo getOperatorInfoFromCellInfoEx(CellInfo cellInfo) {
        OperatorInfo oi;
        if (cellInfo instanceof CellInfoLte) {
            CellInfoLte lte = (CellInfoLte) cellInfo;

            oi = new OperatorInfo(getOperatorAlphaLong(CELL_INFO_TYPE_LTE, lte),
                    (String) lte.getCellIdentity().getOperatorAlphaShort(),
                    lte.getCellIdentity().getMobileNetworkOperator() + " 7");
        } else if (cellInfo instanceof CellInfoWcdma) {
            CellInfoWcdma wcdma = (CellInfoWcdma) cellInfo;
            oi = new OperatorInfo(getOperatorAlphaLong(CELL_INFO_TYPE_WCDMA, wcdma),
                    (String) wcdma.getCellIdentity().getOperatorAlphaShort(),
                    wcdma.getCellIdentity().getMobileNetworkOperator() + " 2");
        } else if (cellInfo instanceof CellInfoGsm) {
            CellInfoGsm gsm = (CellInfoGsm) cellInfo;
            oi = new OperatorInfo(getOperatorAlphaLong(CELL_INFO_TYPE_GSM, gsm),
                    (String) gsm.getCellIdentity().getOperatorAlphaShort(),
                    gsm.getCellIdentity().getMobileNetworkOperator() + " 0");
        } else if (cellInfo instanceof CellInfoCdma) {
            CellInfoCdma cdma = (CellInfoCdma) cellInfo;
            oi = new OperatorInfo(
                    (String) cdma.getCellIdentity().getOperatorAlphaLong(),
                    (String) cdma.getCellIdentity().getOperatorAlphaShort(),
                    "" /* operator numeric */);
        } else if (cellInfo instanceof CellInfoTdscdma) {
            CellInfoTdscdma tdscdma = (CellInfoTdscdma) cellInfo;
            oi = new OperatorInfo(getOperatorAlphaLong(CELL_INFO_TYPE_TDSCDMA, tdscdma),
                    (String) tdscdma.getCellIdentity().getOperatorAlphaShort(),
                    tdscdma.getCellIdentity().getMobileNetworkOperator() + " 2");
        } else {
            oi = new OperatorInfo("", "", "");
        }
        return oi;
    }
    /** @} */

    /** UNISOC: Wrap a cell info into an operator info. @{ */
    public static String getOperatorAlphaLong (int type, CellInfo cellInfo){
        // If operator name is empty, show plmn numeric instead.
        String operatorAlphaLong = (String) cellInfo.getCellIdentity().getOperatorAlphaLong();
        String plmn = cellInfo.getCellIdentity().getMccString() +
                cellInfo.getCellIdentity().getMncString();
        if (operatorAlphaLong == null || operatorAlphaLong.equals("")) {
            operatorAlphaLong = plmn;
        }

        operatorAlphaLong = TeleUtils.translateOperatorName(plmn, operatorAlphaLong);

        // Only append RAT words when operator name not end with 2/3/4G
        if (!operatorAlphaLong.matches(".*[234]G$")) {
            switch(type){
            case CELL_INFO_TYPE_GSM:
                operatorAlphaLong += " 2G";
                break;
            case CELL_INFO_TYPE_WCDMA:
            case CELL_INFO_TYPE_TDSCDMA:
                operatorAlphaLong += " 3G";
                break;
            case CELL_INFO_TYPE_LTE:
                operatorAlphaLong += " 4G";
                break;
            }
        }
        return operatorAlphaLong;
    }
    /** @} */

    /**
     * Creates a CellInfo object from OperatorInfo. GsmCellInfo is used here only because
     * operatorInfo does not contain technology type while CellInfo is an abstract object that
     * requires to specify technology type. It doesn't matter which CellInfo type to use here, since
     * we only want to wrap the operator info and PLMN to a CellInfo object.
     */
    public static CellInfo convertOperatorInfoToCellInfo(OperatorInfo operatorInfo) {
        String operatorNumeric = operatorInfo.getOperatorNumeric();
        String mcc = null;
        String mnc = null;
        if (operatorNumeric != null && operatorNumeric.matches("^[0-9]{5,6}$")) {
            mcc = operatorNumeric.substring(0, 3);
            mnc = operatorNumeric.substring(3);
        }
        CellIdentityGsm cig = new CellIdentityGsm(
                Integer.MAX_VALUE /* lac */,
                Integer.MAX_VALUE /* cid */,
                Integer.MAX_VALUE /* arfcn */,
                Integer.MAX_VALUE /* bsic */,
                mcc,
                mnc,
                operatorInfo.getOperatorAlphaLong(),
                operatorInfo.getOperatorAlphaShort());

        CellInfoGsm ci = new CellInfoGsm();
        ci.setCellIdentity(cig);
        return ci;
    }

    /** Checks whether the network operator is forbidden. */
    public static boolean isForbidden(CellInfo cellInfo, List<String> forbiddenPlmns) {
        String plmn = CellInfoUtil.getOperatorInfoFromCellInfo(cellInfo).getOperatorNumeric();
        return forbiddenPlmns != null && forbiddenPlmns.contains(plmn);
    }

    /** Convert a list of cellInfos to readable string without sensitive info. */
    public static String cellInfoListToString(List<CellInfo> cellInfos) {
        return cellInfos.stream()
                .map(cellInfo -> cellInfoToString(cellInfo))
                .collect(Collectors.joining(", "));
    }

    /** Convert {@code cellInfo} to a readable string without sensitive info. */
    public static String cellInfoToString(CellInfo cellInfo) {
        String cellType = cellInfo.getClass().getSimpleName();
        CellIdentity cid = cellInfo.getCellIdentity();
        return String.format(
                "{CellType = %s, isRegistered = %b, mcc = %s, mnc = %s, alphaL = %s, alphaS = %s}",
                cellType, cellInfo.isRegistered(), cid.getMccString(), cid.getMncString(),
                cid.getOperatorAlphaLong(), cid.getOperatorAlphaShort());
    }
}
