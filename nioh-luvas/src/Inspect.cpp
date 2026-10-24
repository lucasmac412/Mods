// Read-only installation inspector. No injection, patching, input hooks or networking.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <commdlg.h>
#include <shellapi.h>
#include <tlhelp32.h>
#include <bcrypt.h>
#include "PeInfo.h"
#include <algorithm>
#include <array>
#include <cwchar>
#include <cwctype>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

#pragma comment(lib,"bcrypt.lib")
#pragma comment(lib,"version.lib")
#pragma comment(lib,"comdlg32.lib")
#pragma comment(lib,"shell32.lib")
namespace fs=std::filesystem;
namespace {
struct Handle {
    HANDLE value{};
    explicit Handle(HANDLE h):value(h){}
    ~Handle(){if(value && value!=INVALID_HANDLE_VALUE)CloseHandle(value);}
    Handle(const Handle&)=delete; Handle& operator=(const Handle&)=delete;
};
std::string utf8(const std::wstring& s) {
    if(s.empty())return {};
    const auto size=WideCharToMultiByte(CP_UTF8,0,s.data(),int(s.size()),nullptr,0,nullptr,nullptr);
    if(size<=0)throw std::runtime_error("UTF-8 conversion failed");
    std::string out(std::size_t(size),'\0');
    WideCharToMultiByte(CP_UTF8,0,s.data(),int(s.size()),out.data(),size,nullptr,nullptr); return out;
}
std::string json(const std::string& s) {
    std::ostringstream out; out<<'"';
    for(unsigned char c:s) {
        if(c=='"'||c=='\\')out<<'\\'<<char(c);
        else if(c<32)out<<"\\u"<<std::hex<<std::setw(4)<<std::setfill('0')<<unsigned(c)<<std::dec;
        else out<<char(c);
    }
    out<<'"';return out.str();
}
class Sha {
    BCRYPT_ALG_HANDLE algorithm_{}; BCRYPT_HASH_HANDLE hash_{};
    std::vector<unsigned char> object_;
public:
    Sha(){
        if(BCryptOpenAlgorithmProvider(&algorithm_,BCRYPT_SHA256_ALGORITHM,nullptr,0)<0)
            throw std::runtime_error("SHA-256 provider failed");
        DWORD length{},got{};
        if(BCryptGetProperty(algorithm_,BCRYPT_OBJECT_LENGTH,reinterpret_cast<PUCHAR>(&length),sizeof(length),&got,0)<0){
            BCryptCloseAlgorithmProvider(algorithm_,0);algorithm_=nullptr;throw std::runtime_error("SHA-256 property failed");}
        object_.resize(length);
        if(BCryptCreateHash(algorithm_,&hash_,object_.data(),length,nullptr,0,0)<0){
            BCryptCloseAlgorithmProvider(algorithm_,0);algorithm_=nullptr;throw std::runtime_error("SHA-256 init failed");}
    }
    ~Sha(){if(hash_)BCryptDestroyHash(hash_);if(algorithm_)BCryptCloseAlgorithmProvider(algorithm_,0);}
    Sha(const Sha&)=delete;Sha& operator=(const Sha&)=delete;
    void add(const unsigned char* bytes,ULONG count){
        if(BCryptHashData(hash_,const_cast<PUCHAR>(bytes),count,0)<0)throw std::runtime_error("SHA-256 update failed");}
    std::string finish(){
        std::array<unsigned char,32> bytes{};
        if(BCryptFinishHash(hash_,bytes.data(),ULONG(bytes.size()),0)<0)throw std::runtime_error("SHA-256 finish failed");
        std::ostringstream out;for(auto b:bytes)out<<std::hex<<std::setw(2)<<std::setfill('0')<<unsigned(b);return out.str();
    }
};
std::string fileHash(const fs::path& path){
    std::ifstream file(path,std::ios::binary);if(!file)throw std::runtime_error("Cannot read selected file");
    Sha hash;std::array<unsigned char,65536> block{};
    while(file){file.read(reinterpret_cast<char*>(block.data()),block.size());
        if(file.gcount()>0)hash.add(block.data(),ULONG(file.gcount()));}
    if(!file.eof())throw std::runtime_error("File read interrupted");return hash.finish();
}
std::vector<unsigned char> headers(const fs::path& path){
    std::ifstream file(path,std::ios::binary);if(!file)throw std::runtime_error("Cannot read executable headers");
    std::vector<unsigned char> bytes(1024*1024);file.read(reinterpret_cast<char*>(bytes.data()),std::streamsize(bytes.size()));
    bytes.resize(std::size_t(file.gcount()));return bytes;
}
std::string version(const fs::path& path){
    DWORD unused{};const auto size=GetFileVersionInfoSizeW(path.c_str(),&unused);if(!size)return "not_available";
    std::vector<unsigned char> data(size);if(!GetFileVersionInfoW(path.c_str(),0,size,data.data()))return "not_available";
    VS_FIXEDFILEINFO* info{};UINT length{};
    if(!VerQueryValueW(data.data(),L"\\",reinterpret_cast<void**>(&info),&length)||length<sizeof(*info))return "not_available";
    std::ostringstream out;out<<HIWORD(info->dwFileVersionMS)<<'.'<<LOWORD(info->dwFileVersionMS)<<'.'
        <<HIWORD(info->dwFileVersionLS)<<'.'<<LOWORD(info->dwFileVersionLS);return out.str();
}
DWORD findGame(const fs::path& selected){
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS,0));if(snapshot.value==INVALID_HANDLE_VALUE)return 0;
    PROCESSENTRY32W entry{};entry.dwSize=sizeof(entry);
    if(Process32FirstW(snapshot.value,&entry))do{
        if(_wcsicmp(entry.szExeFile,L"nioh.exe"))continue;
        Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,FALSE,entry.th32ProcessID));if(!process.value)continue;
        std::array<wchar_t,32768> path{};DWORD size=DWORD(path.size());
        if(QueryFullProcessImageNameW(process.value,0,path.data(),&size) && !_wcsicmp(fs::path(path.data()).c_str(),selected.c_str()))
            return entry.th32ProcessID;
    }while(Process32NextW(snapshot.value,&entry));return 0;
}
std::string runtime(DWORD pid,const moonfist::PeInfo& pe){
    if(!pid)return "{\"running\":false,\"read_access\":false,\"text_sha256\":null,\"modules\":[]}";
    Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION|PROCESS_VM_READ,FALSE,pid));
    if(!process.value)return "{\"running\":true,\"read_access\":false,\"text_sha256\":null,\"modules\":[]}";
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPMODULE|TH32CS_SNAPMODULE32,pid));
    MODULEENTRY32W entry{};entry.dwSize=sizeof(entry);std::uintptr_t base{};std::ostringstream modules;bool first=true;unsigned count{};
    if(snapshot.value!=INVALID_HANDLE_VALUE && Module32FirstW(snapshot.value,&entry))do{
        if(!count)base=reinterpret_cast<std::uintptr_t>(entry.modBaseAddr);
        if(count++>=128)break;if(!first)modules<<',';first=false;modules<<json(utf8(entry.szModule));
    }while(Module32NextW(snapshot.value,&entry));
    std::string textHash="null";bool read=false;
    if(base)for(const auto& section:pe.sections){
        if(section.name!=".text"||!section.virtualSize||section.virtualSize>128*1024*1024)continue;
        Sha hash;std::array<unsigned char,32768> bytes{};std::uint32_t offset{};read=true;
        while(offset<section.virtualSize){const SIZE_T wanted=std::min<std::size_t>(bytes.size(),section.virtualSize-offset);SIZE_T got{};
            if(!ReadProcessMemory(process.value,reinterpret_cast<const void*>(base+section.rva+offset),bytes.data(),wanted,&got)||got!=wanted){read=false;break;}
            hash.add(bytes.data(),ULONG(got));offset+=std::uint32_t(got);
        }
        if(read)textHash=json(hash.finish());break;
    }
    return "{\"running\":true,\"read_access\":"+std::string(read?"true":"false")+",\"text_sha256\":"+textHash+",\"modules\":["+modules.str()+"]}";
}
std::string catalogue(const fs::path& folder){
    std::ostringstream out;bool first=true;unsigned count{};std::error_code error;
    fs::recursive_directory_iterator it(folder,fs::directory_options::skip_permission_denied,error),end;
    while(it!=end && count<256){
        if(it.depth()>=2 && it->is_directory(error))it.disable_recursion_pending();
        if(it->is_regular_file(error)){
            auto ext=it->path().extension().wstring();std::transform(ext.begin(),ext.end(),ext.begin(),[](wchar_t c){return wchar_t(towlower(c));});
            if(ext==L".bin"||ext==L".idx"||ext==L".lnk"||ext==L".fdata"||ext==L".dat"||ext==L".pak"||ext==L".g1m"||ext==L".g1a"){
                const auto bytes=it->file_size(error);if(!error){if(!first)out<<',';first=false;++count;
                    out<<"{\"file\":"<<json(utf8(it->path().lexically_relative(folder).generic_wstring()))<<",\"bytes\":"<<bytes<<'}';}
            }
        }
        error.clear();it.increment(error);if(error)break;
    }
    return '['+out.str()+']';
}
std::string report(const fs::path& game,DWORD pid){
    const auto pe=moonfist::peInfo(headers(game));std::ostringstream out;
    out<<"{\n\"schema\":1,\n\"tool\":\"Nioh Inspector 0.1.0 - read only\",\n"
        "\"engine_bindings\":\"unresolved\",\n\"game_writes\":false,\n\"network_used\":false,\n"
        "\"absolute_paths_included\":false,\n\"file_version\":"<<json(version(game))<<",\n\"exe_sha256\":"<<json(fileHash(game))
        <<",\n\"pe_machine\":"<<pe.machine<<",\n\"pe_timestamp\":"<<pe.timestamp<<",\n\"image_size\":"<<pe.imageSize<<",\n\"sections\":[";
    bool first=true;for(const auto& section:pe.sections){if(!first)out<<',';first=false;
        out<<"{\"name\":"<<json(section.name)<<",\"rva\":"<<section.rva<<",\"virtual_size\":"<<section.virtualSize<<",\"raw_size\":"<<section.rawSize<<'}';}
    out<<"],\n\"runtime\":"<<runtime(pid,pe)<<",\n\"archive_catalogue\":"<<catalogue(game.parent_path())<<"\n}\n";return out.str();
}
fs::path selfPath(){std::array<wchar_t,32768> path{};const auto size=GetModuleFileNameW(nullptr,path.data(),DWORD(path.size()));
    if(!size||size==path.size())throw std::runtime_error("Cannot determine tool location");return fs::path(path.data());}
bool pick(bool save,fs::path& path){
    std::array<wchar_t,32768> buffer{};if(save)wcscpy_s(buffer.data(),buffer.size(),L"Nioh-Diagnostico.json");
    OPENFILENAMEW dialog{};dialog.lStructSize=sizeof(dialog);dialog.lpstrFile=buffer.data();dialog.nMaxFile=DWORD(buffer.size());
    dialog.lpstrTitle=save?L"Salvar relatório para enviar nesta conversa":L"Escolha nioh.exe na pasta do Nioh da Steam";
    dialog.lpstrFilter=save?L"Relatório JSON\0*.json\0\0":L"Nioh (nioh.exe)\0nioh.exe\0\0";
    dialog.lpstrDefExt=save?L"json":L"exe";
    dialog.Flags=OFN_PATHMUSTEXIST|OFN_NOCHANGEDIR|(save?OFN_OVERWRITEPROMPT:OFN_FILEMUSTEXIST);
    if(!(save?GetSaveFileNameW(&dialog):GetOpenFileNameW(&dialog)))return false;
    path=fs::absolute(buffer.data());return true;
}
} // namespace
int WINAPI wWinMain(HINSTANCE,HINSTANCE,PWSTR arguments,int){
    try {
        if(arguments && std::wstring(arguments)==L"/self-test"){
            Sha hash;hash.add(reinterpret_cast<const unsigned char*>("abc"),3);
            if(hash.finish()!="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")return 2;
            const auto data=report(selfPath(),GetCurrentProcessId());
            if(data.find("\"read_access\":true")==std::string::npos)return 3;
            std::ofstream out("inspector-self-test.json",std::ios::binary);out<<data;return out?0:4;
        }
        MessageBoxW(nullptr,L"Esta ferramenta identifica a instalação do Nioh 1. Ela não instala as luvas nem modifica arquivos ou memória do jogo.\n\nAbra o Nioh e deixe no menu inicial. Depois, escolha nioh.exe na pasta que a Steam abre em Gerenciar > Explorar arquivos locais.",L"Nioh — identificação da instalação",MB_OK|MB_ICONINFORMATION);
        fs::path game,output;if(!pick(false,game))return 0;
        if(_wcsicmp(game.filename().c_str(),L"nioh.exe"))throw std::runtime_error("Choose nioh.exe from Nioh 1, not a launcher or Nioh 2.");
        const auto pid=findGame(game);const auto data=report(game,pid);if(!pick(true,output))return 0;
        if(_wcsicmp(output.extension().c_str(),L".json"))throw std::runtime_error("Report must use the .json extension.");
        std::ofstream out(output,std::ios::binary|std::ios::trunc);out<<data;out.close();if(!out)throw std::runtime_error("Could not save the report.");
        MessageBoxW(nullptr,pid?L"Relatório salvo. Envie Nioh-Diagnostico.json nesta conversa. Nenhum arquivo ou memória do jogo foi alterado.":L"Relatório salvo, mas o jogo aberto não foi localizado. Abra o Nioh, deixe no menu inicial e execute a ferramenta novamente para incluir a identificação em memória.",L"Relatório concluído",MB_OK|MB_ICONINFORMATION);return 0;
    } catch(const std::exception& e){MessageBoxA(nullptr,e.what(),"Nioh Inspector - could not complete",MB_OK|MB_ICONERROR);return 1;}
}
