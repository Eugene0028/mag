import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        System.out.println(reverse(1534236469));
    }



    public static int reverse(int x) {

        long y = 0;
        long bound = Integer.MAX_VALUE;
        boolean sign = x < 0;
        x = Math.abs(x);
        while (x > 0) {
            y *= 10;
            if (bound < y) {
                return 0;
            }
            y = (y + (x % 10));
            x /= 10;
        }
        return Math.toIntExact(sign ? y * -1 : y);
    }

    public static int lengthOfLongestSubstring(String s) {
        var arr = s.toCharArray();
        var set = new HashSet<Character>();

        var l = 0;
        var r = 0;
        var mx = 0;

        while (r < arr.length) {
            if (!set.add(arr[r])) {
                while (arr[l] != arr[r]) {
                    set.remove(arr[l++]);
                }
                set.remove(arr[l++]);
            } else {
                r++;
                if (mx < r-l) mx = r-l;
            }

        }
        System.out.println(set);

        return mx;
    }
}