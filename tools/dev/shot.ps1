param([string]$Out, [int]$WaitSeconds = 120)
# Waits for the app window, then captures only that window.
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System; using System.Runtime.InteropServices;
public class W { [StructLayout(LayoutKind.Sequential)] public struct R { public int L, T, Ri, B; }
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
[DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f); }
'@
$p = $null
for ($i = 0; $i -lt $WaitSeconds -and -not $p; $i++) {
    $p = Get-Process -Name java,javaw,"RANN's Roost" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'RANN*s Roost*' } | Select-Object -First 1
    if (-not $p) { Start-Sleep -Seconds 1 }
}
if (-not $p) { "no window"; exit 1 }
Start-Sleep -Seconds 4
$h = $p.MainWindowHandle
$r = New-Object W+R; [W]::GetWindowRect($h, [ref]$r) | Out-Null
$bmp = New-Object System.Drawing.Bitmap ($r.Ri - $r.L), ($r.B - $r.T)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$dc = $g.GetHdc(); [W]::PrintWindow($h, $dc, 2) | Out-Null; $g.ReleaseHdc($dc)
$bmp.Save($Out)
"saved pid=$($p.Id)"
