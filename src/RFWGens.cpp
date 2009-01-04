/*
	SuperCollider real time audio synthesis system
    Copyright (c) 2002 James McCartney. All rights reserved.
	http://www.audiosynth.com
 
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.
 
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

//ACMC demo UGen

#include "SC_PlugIn.h"

static InterfaceTable *ft; 

struct AverageOutput : public Unit  {
    float average, prev_trig;
    uint32 count;
};

extern "C" {  
	void AverageOutput_next(AverageOutput *unit, int inNumSamples);
	void AverageOutput_Ctor(AverageOutput* unit);
	void AverageOutput_Dtor(AverageOutput* unit);
}


void AverageOutput_Ctor( AverageOutput* unit ) {
    unit->average = 0.;
    unit->count = 0;
    unit->prev_trig = 0.;
	
	RGen& rgen = *unit->mParent->mRGen;
	
	SETCALC(AverageOutput_next);
}

void AverageOutput_Dtor(AverageOutput *unit) {
    // nothing to do here
}

void AverageOutput_next( AverageOutput *unit, int inNumSamples ) {
    int i;
    float *in = IN(0);
    float *out = ZOUT(0);
    float trig = ZIN0(1);
    float prev_trig = unit->prev_trig;
    double average = unit->average;
    uint32 count = unit->count;
    
    if(prev_trig <= 0. && trig > 0.) {
        average = 0.;
        count = 0;
    }
    		
	for (i=0; i<inNumSamples; ++i) {
        average = ((count * average) + *(in+i)) / ++count;
        ZXP(out) = average;
	}
    
    unit->prev_trig = trig;
	  unit->count = count;
    unit->average = average;
}	

extern "C" void load(InterfaceTable *inTable) {
	
	ft = inTable;
	
	DefineDtorUnit(AverageOutput);
	
}


