Option Explicit

Dim shell, fso, baseDir, launcherPath, command
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
baseDir = fso.GetParentFolderName(WScript.ScriptFullName)
launcherPath = fso.BuildPath(baseDir, "launcher\launcher.ps1")
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & launcherPath & """"
shell.Run command, 0, False
