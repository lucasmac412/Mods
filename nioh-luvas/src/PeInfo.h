// Bounded PE header metadata reader. Does not load or execute the inspected file.
#pragma once
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace moonfist {
struct Section { std::string name; std::uint32_t rva, virtualSize, rawSize, rawOffset, flags; };
struct PeInfo { std::uint16_t machine; std::uint32_t timestamp, imageSize; std::vector<Section> sections; };
inline PeInfo peInfo(const std::vector<unsigned char>& data) {
    const auto read = [&](std::size_t at, unsigned bytes) -> std::uint32_t {
        if (at > data.size() || bytes > data.size() - at) throw std::runtime_error("Truncated PE header");
        std::uint32_t result{};
        for (unsigned i=0;i<bytes;++i) result |= std::uint32_t(data[at+i]) << (8*i);
        return result;
    };
    if (read(0,2) != 0x5A4D) throw std::runtime_error("Not a Windows executable");
    const auto pe=read(0x3C,4);
    if (pe > 1048000 || read(pe,4) != 0x4550) throw std::runtime_error("Invalid PE offset/signature");
    PeInfo result{std::uint16_t(read(pe+4,2)),read(pe+8,4),0,{}};
    const auto count=read(pe+6,2), optional=read(pe+20,2), magic=read(pe+24,2);
    if (count==0 || count>96 || optional<112 || optional>1024 || magic!=0x20B || result.machine!=0x8664)
        throw std::runtime_error("Expected a bounded x64 PE executable");
    result.imageSize=read(pe+24+56,4);
    if (!result.imageSize || result.imageSize>0x80000000U) throw std::runtime_error("Invalid image size");
    for (std::uint32_t i=0;i<count;++i) {
        const auto at=pe+24+optional+i*40;
        std::string name;
        for (unsigned j=0;j<8;++j) { const auto c=read(at+j,1); if(c) name+=char(c); else break; }
        Section section{name,read(at+12,4),read(at+8,4),read(at+16,4),read(at+20,4),read(at+36,4)};
        if (section.rva>result.imageSize || section.virtualSize>result.imageSize-section.rva)
            throw std::runtime_error("Section outside declared image");
        result.sections.push_back(section);
    }
    return result;
}
} // namespace moonfist
