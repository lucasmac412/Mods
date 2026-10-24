#include "Combat.h"
#include <iostream>
#include <limits>
#include <stdexcept>

unsigned checks{};
void check(bool pass, const char* message) { ++checks; if (!pass) throw std::runtime_error(message); }
int main() {
    using namespace moonfist;
    try {
        for (unsigned stance = 0; stance < 3; ++stance) for (int fps : {30,60,120}) {
            for (auto command : {Command::Light,Command::Heavy,Command::Caps}) {
                Combat c; check(c.stance(stance), "stance selection");
                check(c.input(command,true), "legal attack starts");
                const auto* move = c.active();
                check(c.ki() == 100 - move->ki, "charge Ki exactly once");
                unsigned hits{};
                for (int frame = 0; frame < fps * 2; ++frame) {
                    check(!c.input(command,true), "held Caps/attack cannot repeat");
                    for (auto impact : c.tick(1.0 / fps)) {
                        check(impact.hit == hits, "hit windows ordered without duplicates");
                        check(impact.move == move, "events retain correct move"); ++hits;
                    }
                }
                check(hits == move->hitCount && !c.active(), "all hit windows occur at every tested frame rate");
            }
        }
        for (unsigned block = 0; block < 5; ++block) {
            Combat c; Gate g;
            check(c.input(Command::Caps,true), "begin special"); c.input(Command::Caps,false);
            switch (block) { case 0:g.player=false;break;case 1:g.gameplay=false;break;
                case 2:g.alive=false;break;case 3:g.equipped=false;break;case 4:g.interrupted=true;break; }
            c.gate(g);
            check(!c.active() && c.tick(1).empty(), "interruption prevents future damage events");
            check(!c.input(Command::Light,true), "no input in menus/death/NPC/unequipped state");
            c.gate({});
            check(!c.input(Command::Light,true), "returning to game cannot replay a held input");
            c.input(Command::Light,false); check(c.input(Command::Light,true), "fresh edge after resume works");
        }
        Combat caps; check(caps.input(Command::Caps,true), "special starts"); caps.input(Command::Caps,false);
        for (unsigned n=0;n<20;++n) caps.tick(.1);
        check(!caps.input(Command::Caps,true), "cooldown rejects spam"); caps.input(Command::Caps,false);
        for (unsigned n=0;n<100;++n) caps.tick(.1);
        check(caps.input(Command::Caps,true), "special can be used again after cooldown");
        Combat invalid; invalid.input(Command::Light,true);
        for (double value : {-1.,0.,std::numeric_limits<double>::infinity(),std::numeric_limits<double>::quiet_NaN()})
            check(invalid.tick(value).empty(), "invalid time cannot emit an impact");
        check(invalid.tick(100).empty(), "large stall does not skip straight to impact");
        Combat cost; cost.stance(2);
        for (int i=0;i<3;++i) { check(cost.input(Command::Heavy,true), "heavy consumes finite Ki");
            cost.input(Command::Heavy,false); for (int n=0;n<13;++n) cost.tick(.1); }
        check(cost.ki() < 30 && !cost.input(Command::Heavy,true), "insufficient Ki rejects without going negative");
        HitLedger ledger; Impact impact{0,&moves[0],1};
        check(!ledger.accept(impact,1,1,false,1), "owner excluded");
        check(!ledger.accept(impact,2,1,true,1), "ally excluded");
        check(!ledger.accept(impact,2,1,false,100), "range checked");
        check(ledger.accept(impact,2,1,false,1), "enemy inside range accepted");
        check(!ledger.accept(impact,2,1,false,1), "same impact cannot damage enemy twice");
        impact.hit=1; check(ledger.accept(impact,2,1,false,1), "next combo impact may hit again");
        impact.hit=0; check(!ledger.accept(impact,2,1,false,1), "out-of-order replay cannot hit the same enemy twice");
        impact.attack=2; check(ledger.accept(impact,2,1,false,1), "a fresh attack may hit again");
        impact.attack=1; check(!ledger.accept(impact,3,1,false,1), "stale attack cannot rewind hit deduplication");
        std::cout << "PASS: " << checks << " combat simulation checks; Nioh engine not executed.\n";
        return 0;
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
