#include "PeInfo.h"
#include <iostream>
#include <stdexcept>
unsigned checks{};
void check(bool pass,const char* text){++checks;if(!pass)throw std::runtime_error(text);}
void put(std::vector<unsigned char>& data,unsigned at,unsigned value,unsigned bytes){
    for(unsigned n=0;n<bytes;++n)data.at(at+n)=static_cast<unsigned char>((value>>(n*8))&255);
}
bool rejects(const std::vector<unsigned char>& data){try{moonfist::peInfo(data);return false;}catch(const std::runtime_error&){return true;}}
int main(){try{
    std::vector<unsigned char> data(512);
    put(data,0,0x5a4d,2);put(data,0x3c,0x80,4);put(data,0x80,0x4550,4);
    put(data,0x84,0x8664,2);put(data,0x86,1,2);put(data,0x94,0xf0,2);put(data,0x98,0x20b,2);put(data,0x98+56,0x3000,4);
    const unsigned section=0x98+0xf0;
    const std::string name=".text";for(unsigned i=0;i<name.size();++i)data[section+i]=static_cast<unsigned char>(name[i]);
    put(data,section+8,0x1000,4);put(data,section+12,0x1000,4);put(data,section+16,0x200,4);put(data,section+20,0x200,4);put(data,section+36,0x60000020,4);
    const auto before=data;const auto info=moonfist::peInfo(data);
    check(data==before,"inspection must not mutate source bytes");
    check(info.machine==0x8664 && info.imageSize==0x3000 && info.sections.size()==1,"x64 headers");
    check(info.sections[0].name==".text" && info.sections[0].rva==0x1000,"section metadata");
    for(unsigned n=0;n<section+40;++n){auto shortFile=data;shortFile.resize(n);check(rejects(shortFile),"every truncated header boundary rejected");}
    for(auto offset:{0U,0x3cU,0x80U,0x84U,0x86U,0x94U,0x98U,0x98U+56,section+8,section+12}){
        auto invalid=data;put(invalid,offset,0xffffffff,4);check(rejects(invalid),"invalid bounds/magic/architecture rejected");
    }
    std::cout<<"PASS: "<<checks<<" PE metadata checks; no inspected file executed.\n";return 0;
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
