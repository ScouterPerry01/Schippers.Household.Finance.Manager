param([int]$X, [int]$Y, [string]$Out, [int]$Wheel = 0)
# Clicks at (X, Y) relative to the app window's top-left corner (or, with -Wheel, scrolls there:
# negative values scroll down, 120 per notch), then optionally captures the window.
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System; using System.Runtime.InteropServices;
public class C { [StructLayout(LayoutKind.Sequential)] public struct R { public int L, T, Ri, B; }
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
[DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
[DllImport("user32.dll")] public static extern void mouse_event(uint f, int x, int y, uint d, IntPtr e);
[DllImport("user32.dll", EntryPoint = "mouse_event")] public static extern void wheel(uint f, int x, int y, int d, IntPtr e);
[DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f); }
'@
$p = Get-Process -Name java,javaw,"RANN's Roost" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'RANN*s Roost*' } | Select-Object -First 1
if (-not $p) { "no window"; exit 1 }
$h = $p.MainWindowHandle
$r = New-Object C+R; [C]::GetWindowRect($h, [ref]$r) | Out-Null
[C]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 300
if ($X -gt 0) {
    [C]::SetCursorPos($r.L + $X, $r.T + $Y) | Out-Null
    if ($Wheel -ne 0) { [C]::wheel(0x0800, 0, 0, $Wheel, [IntPtr]::Zero) }
    else { [C]::mouse_event(2, 0, 0, 0, [IntPtr]::Zero); [C]::mouse_event(4, 0, 0, 0, [IntPtr]::Zero) }
    Start-Sleep -Seconds 2
}
if ($Out) {
    $bmp = New-Object System.Drawing.Bitmap ($r.Ri - $r.L), ($r.B - $r.T)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $dc = $g.GetHdc(); [C]::PrintWindow($h, $dc, 2) | Out-Null; $g.ReleaseHdc($dc)
    $bmp.Save($Out)
    "saved"
}
