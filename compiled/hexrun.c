#include <stdio.h>
#include <stdlib.h>
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

void dealloc(void* ptr) {
    free(ptr);
}

void* builtInFunctions[] = { (void*)print, (void*)scan, (void*)alloc, (void*)dealloc };

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

    // Calculate the total size needed
    size_t totalSize = sizeof(builtInFunctions) + fileSize;

    // Allocate executable memory
    void* mem = VirtualAlloc(NULL, totalSize, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (mem == NULL) {
        perror("VirtualAlloc failed");
        fclose(file);
        return 1;
    }

    // Copy built-in functions to the allocated memory

    // Read binary file into the allocated memory
    if (fread(mem, 1, fileSize, file) != fileSize) {
        perror("Failed to read file");
        fclose(file);
        VirtualFree(mem, 0, MEM_RELEASE);
        return 1;
    }
    fclose(file);
    memcpy(mem, builtInFunctions, sizeof(builtInFunctions));

    // Change the memory protection to executable
    DWORD oldProtect;
    if (!VirtualProtect(mem, totalSize, PAGE_EXECUTE_READ, &oldProtect)) {
        perror("VirtualProtect failed");
        VirtualFree(mem, 0, MEM_RELEASE);
        return 1;
    }

    // Interpret the function pointer from the allocated memory
    u64(*func)() = (u64(*)())((char*)mem + sizeof(builtInFunctions));
    u64 result = func();  // Call the function

    printf("Function returned: %llu\n", result);

    VirtualFree(mem, 0, MEM_RELEASE);
    return 0;
}
