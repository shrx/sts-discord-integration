package shrx.discordintegration;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

public final class Win32API {

    private Win32API() {}

    public static WindowInfo getWindowInfo(int hWnd) {
        RECT r = new RECT();
        User32.instance.GetWindowRect(hWnd, r);
        byte[] buffer = new byte[1024];
        User32.instance.GetWindowTextA(hWnd, buffer, buffer.length);
        String title = Native.toString(buffer);
        return new WindowInfo(hWnd, r, title);
    }

    public interface WndEnumProc extends StdCallLibrary.StdCallCallback {
        boolean callback(int hWnd, int lParam);
    }

    public interface User32 extends StdCallLibrary {
        String SHELL_TRAY_WND = "Shell_TrayWnd";
        int WM_COMMAND = 0x111;
        int MIN_ALL = 0x1a3;
        int MIN_ALL_UNDO = 0x1a0;

        User32 instance = Native.loadLibrary("user32", User32.class);

        boolean EnumWindows(WndEnumProc wndenumproc, int lParam);

        boolean IsWindowVisible(int hWnd);

        int GetWindowRect(int hWnd, RECT r);

        void GetWindowTextA(int hWnd, byte[] buffer, int buflen);

        int GetTopWindow(int hWnd);

        int GetWindow(int hWnd, int flag);

        boolean ShowWindow(int hWnd);

        boolean BringWindowToTop(int hWnd);

        int GetActiveWindow();

        boolean SetForegroundWindow(int hWnd);

        int FindWindowA(String winClass, String title);

        long SendMessageA(int hWnd, int msg, int num1, int num2);

        int GW_HWNDNEXT = 2;
    }

    public static class RECT extends Structure {
        public int left, top, right, bottom;

        @Override
        protected List<String> getFieldOrder() {
            final List<String> order = new ArrayList<>();
            order.add("left");
            order.add("top");
            order.add("right");
            order.add("bottom");
            return order;
        }
    }

    public static class WindowInfo {
        int hwnd;
        RECT rect;
        String title;

        public WindowInfo(int hwnd, RECT rect, String title) {
            this.hwnd = hwnd;
            this.rect = rect;
            this.title = title;
        }

        public String toString() {
            return String.format("(%d,%d)-(%d,%d) : \"%s\"", rect.left, rect.top, rect.right, rect.bottom, title);
        }
    }
}
