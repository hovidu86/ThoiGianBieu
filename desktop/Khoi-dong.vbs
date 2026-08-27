' Chay KiemSoatMay.ps1 an hoan toan, khong nhay cua so console.
Option Explicit
Dim sh, fso, thuMuc, lenh
Set sh  = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
thuMuc = fso.GetParentFolderName(WScript.ScriptFullName)
lenh = "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -STA -File """ & thuMuc & "\KiemSoatMay.ps1"""
sh.Run lenh, 0, False
