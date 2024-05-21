#include <stdio.h>
#include <windows.h>

typedef unsigned long long u64;


void print(u64 v) {
    printf("> %llu\n", v);
}

u64 scan() {
    u64 v;
    scanf("%llu", &v);
    return v;
}

void* alloc(u64 size) {
    return malloc(size);
}

void dealloc(u64* ptr) {
    free(ptr);
}


int main(int argc, char* argv[]) {
    if (argc < 2) {
       fprintf(stderr, "Usage: %s <path_to_binary_file>\n", argv[0]);
       return 1;
    }
    const char* filePath = argv[1];
    FILE* file = fopen(filePath, "rb");
    if (file == NULL) {
       perror("Failed to open file");
       return 1;
    }
    fseek(file, 0, SEEK_END);
    long fileSize = ftell(file);
    fseek(file, 0, SEEK_SET);

    // Allocate executable memory
    void* mem = VirtualAlloc(NULL, fileSize, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);

    // Read file into memory
    if (fread(mem, 1, fileSize, file) != fileSize) {
        perror("Failed to read file");
        fclose(file);
        VirtualFree(mem, 0, MEM_RELEASE);
        return 1;
    }
    fclose(file);
    DWORD oldProtect;
    VirtualProtect(mem, fileSize, PAGE_EXECUTE_READ, &oldProtect);
    // Interpret the first part of the memory as int64_t (assuming file is large enough)
    u64 (*func)(u64, u64) = mem;
    u64 result = func(5, 10);  // Example arguments

    printf("Function returned: %llu\n", result);

    VirtualFree(mem, 0, MEM_RELEASE);
    return 0;
}
