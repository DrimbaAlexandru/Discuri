B1=[   4000,   8000,  22050,  22050 ];
B1V=[ -96,   0,   0, -96 ];
B2=[  1000,  2000,  8000,  4000 ];
B2V=[ -96,   0,   0, -96 ];
B3=[    50,    50,  2000,  1000 ];
B3V=[ -96,   0,   0, -96 ];

fill(B1,B1V,[0 1 0.1],B2,B2V,[0.3 0.9 0.4],B3,B3V,[ 0.7 0.3 0.1 ] )
xticks=[50 100 200 400 1000 2000 4000 8000 22050];
xlabels={'50Hz','100Hz','200Hz','400Hz','1000Hz','2000Hz','4000Hz','8000Hz','22050Hz'};
yticks=[0 -12 -24 -36 -48 -60 -72 -84 -96];
ylabels={'0dB','-12dB','-24dB','-36dB', '-48dB', '-60dB', '-72dB', '-84dB', '-96dB' };
axis([50 22050 -102 6])
set(gca, 'XScale', 'log')
set(gca, 'XTick',xticks)
set(gca, 'XTickLabel', xlabels)
set(gca, 'YTick',yticks)
set(gca, 'YTickLabel', ylabels)



 