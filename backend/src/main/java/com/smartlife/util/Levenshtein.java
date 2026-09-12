package com.smartlife.util;

/**
 * 编辑距离（Levenshtein Distance）：
 * 用于饭搭子标签相似度计算，中文按字符处理，采用滚动数组将空间复杂度降为 O(min(m,n))
 */
public class Levenshtein {

    public static int distance(String a, String b) {
        if (a == null || b == null) {
            return 999;
        }
        if (a.equals(b)) {
            return 0;
        }
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        // 保证列数较小，降低空间占用
        if (a.length() < b.length()) {
            String tmp = a;
            a = b;
            b = tmp;
        }
        int m = a.length();
        int n = b.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }
}
