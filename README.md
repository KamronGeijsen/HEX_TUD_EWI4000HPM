# HEX---modified-edit-version
 
Each section of the compiler can be run separately (they each have their own main function), but to run the full compiler run the NaiveAssembler. This also automatically invokes hexrun.exe, but will output nothing of the runtime if it finishes gracefully, not running into a segfault or infinite loop.

The compiled/compile hexrun.bat can be modified to target the mingw C++ compiler of choice.
