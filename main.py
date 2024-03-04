#READ ALL CVS FROM ENERGIES DIRECTORY AND UNION IN ONE DATASET
import pandas as pd, os, re
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np


def calcolaEnergiaPerOgniSensore(dfMain):
    listaDataframe=[]
    listaNomiSensori = []
    everyTarget_df = dfMain.groupby("target")

    for group_name, group_data in everyTarget_df:
        print(f"Group: {group_name}")
        listaNomiSensori.append(group_name)
        #raggruppo per ogni gruppo sommo tutti quelli della run 0 e ottengo potenza del target
        everyRun = group_data.groupby("directory")
        group_data.insert(3, "numero_istanze", 0, True)
        group_data.insert(4, "energia", 0, True)
        result_df = everyRun.agg({'power': 'sum','numero_istanze': 'count'}).reset_index()
        #result_df["energia"] = (result_df["power"] * result_df["numero_istanze"] * 0.00027) #calcolo dell'energia
        result_df["energia"] = result_df["power"]
        print(result_df.to_string())
        print("--------------------")
        listaDataframe.append(result_df)
        result_df.to_csv("./separatedDatasets/"+group_name+".csv")

    return listaDataframe, listaNomiSensori

def calcolaEnergiaPerOgniRun(lista,listaSensori):
    datasetTotal = pd.DataFrame(columns=list(range(51)))
    i=0

    for dataset in lista:
        dataset=dataset.drop('power', axis=1)
        dataset=dataset.drop('numero_istanze', axis=1)
        datasetTotal.loc[listaSensori[i]] = dataset.T.loc["energia"]
        i+=1

    datasetTotal.to_csv("./separateRun/uniqueDataset.csv")

    return datasetTotal


def createUniqueDataset(path):
    os.chdir("..")
    dfMain = pd.DataFrame({'name': [], 'time': [], 'power': [], 'sensor': [], 'target': [], 'directory': []})
    os.chdir(path)

    for root, dirs, files in os.walk("./Energies"):
        for filename in files:
            #print(os.path.join(root, filename))
            path=os.path.join(root, filename)
            df = pd.read_csv(path, index_col=False)
            m = re.search("[\d]+", path.split("/")[2]).group()
            #print(m)
            df.insert(5,"directory",int(m),True)
            dfMain=pd.concat([df,dfMain], axis=0, sort=False, ignore_index=True)

    dfMain['time'] = pd.to_datetime(dfMain['time'])
    dfMain=dfMain.reset_index(drop=True)
    dfMain=dfMain.sort_values(by=['directory','time'],ascending=True,ignore_index=True )
    os.chdir("..")
    os.chdir("..")
    os.chdir("./datasetUnion/datasets")

    dfMain.to_csv("mainDataset.csv")
    return dfMain

def EnergyEvolutionForEveryRun(df,dfAF):
    #NON VEDO CONSIDERAZIONI INTERESSANTI DA FARE POST REFACTORING
    fig, axes = plt.subplots(nrows=2, ncols=1)
    box = dict(facecolor='yellow', pad=5, alpha=0.2)

    ax1 = axes[0]
    ax1.plot(df)
    ax1.set_title("Andamento dell'energia per ogni Run (PreRefactoring)", fontweight='bold')

    ax1.set_ylabel("Energia in J", bbox=box)
    ax1.legend()

    ax2 = axes[1]
    ax2.plot(dfAF)
    ax2.set_title("Andamento dell'energia per ogni Run (PostRefactoring)", fontweight='bold')

    ax2.set_ylabel("Energia in J", bbox=box)
    ax2.legend()
    plt.suptitle("Evoluzione dell'energia per ogni run pre e post refactoring", fontsize=16)
    plt.show()



def legend():
    plt.xlabel('Numero Run')
    plt.ylabel('energia totale per Run')
    plt.title('Multiple Lines Plot')
    plt.legend()

def plotForEverySensor(df,dfA,dfARF):
    # blu pre refactoring e arancio post
    print(df)
    print(dfA)
    fig, axes = plt.subplots(nrows=4, ncols=2)
    box = dict(facecolor='yellow', pad=5, alpha=0.2)

    ax1 = axes[0, 0]
    ax1.plot(df.loc["crate-mio-container-master"], color="blue")
    ax1.plot(dfA.loc["crate-container-cd"], color="orange")
    ax1.plot(dfARF.loc["crate-container-cd"], color="red")
    ax1.set_title('crate-container-cd', fontweight='bold')
    ax1.set_ylabel('energia totale per Run', bbox=box)
    ax1.set_xlabel('Numero della Run', bbox=box)

    ax2 = axes[0,1]
    ax2.plot(df.loc["global"], color="blue")
    ax2.plot(dfA.loc["global"], color="orange")
    ax2.plot(dfARF.loc["global"], color="RED")
    ax2.set_title('global', fontweight='bold')
    ax2.set_ylabel('energia totale per Run', bbox=box)
    ax2.set_xlabel('Numero della Run', bbox=box)


    ax3 = axes[1, 0]
    ax3.plot(df.loc["hwpc-sensor-container"], color="blue")
    ax3.plot(dfA.loc["hwpc-sensor-container"], color="orange")
    ax3.plot(dfARF.loc["hwpc-sensor-container"], color="RED")
    ax3.set_title('hwpc-sensor-container', fontweight='bold')
    ax3.set_ylabel('energia totale per Run', bbox=box)
    ax3.set_xlabel('Numero della Run', bbox=box)


    ax4 = axes[1, 1]
    ax4.plot(df.loc["influx_dest"], color="blue")
    ax4.plot(dfA.loc["influx_dest"], color="orange")
    ax4.plot(dfARF.loc["influx_dest"], color="RED")
    ax4.set_title('influx_dest', fontweight='bold')
    ax4.set_ylabel('energia totale per Run', bbox=box)
    ax4.set_xlabel('Numero della Run', bbox=box)

    ax5 = axes[2, 0]
    ax5.plot(df.loc["mongo_source"], color="blue")
    ax5.plot(dfA.loc["mongo_source"], color="orange")
    ax5.plot(dfARF.loc["mongo_source"], color="RED")
    ax5.set_title('mongo_source', fontweight='bold')
    ax5.set_ylabel('energia totale per Run', bbox=box)
    ax5.set_xlabel('Numero della Run', bbox=box)

    ax6 = axes[2, 1]
    ax6.plot(df.loc["rapl"], color="blue")
    ax6.plot(dfA.loc["rapl"], color="orange")
    ax6.plot(dfARF.loc["rapl"], color="RED")
    ax6.set_title('rapl', fontweight='bold')
    ax6.set_ylabel('energia totale per Run', bbox=box)
    ax6.set_xlabel('Numero della Run', bbox=box)

    ax7 = axes[3, 0]
    ax7.plot(df.loc["smartwatts-formula"], color="blue")
    ax7.plot(dfA.loc["smartwatts-formula"], color="orange")
    ax7.plot(dfARF.loc["smartwatts-formula"], color="RED")
    ax7.set_title('smartwatts-formula', fontweight='bold')
    ax7.set_ylabel('energia totale per Run', bbox=box)
    ax7.set_xlabel('Numero della Run', bbox=box)

    fig.subplots_adjust(hspace=0.7)
    plt.suptitle('Evoluzione del consumo di energia per ogni sensore Pre e Post Refactoring', fontsize=16)
    plt.show()
def piechartEnergyTot(df):
    df['avg'] = df.sum(axis=1, numeric_only=True)
    print(df)
    new_order = ["crate-container-cd", "hwpc-sensor-container", "global", "influx_dest", "rapl", "mongo_source",
                 "smartwatts-formula"]

    df_new = df["avg"].reindex(new_order)
    labels = ["crate-container-cd \nE=464004 W", "hwpc-sensor-container \nE=648 W", "global \nE=474197 W", "influx_dest \nE=393 W", "rapl \nE=544576 W", "                                    mongo_source \n                                     E=3477 W",
                 "smartwatts-formula \nE=5673 W"]
    sizes = df_new
    print(sizes)


    fig, ax = plt.subplots(figsize=(6, 3), subplot_kw=dict(aspect="equal"))

    wedges, texts = ax.pie(sizes, wedgeprops=dict(width=0.5), startangle=-40)

    bbox_props = dict(boxstyle="square,pad=0.3", fc="w", ec="k", lw=0.72)
    kw = dict(arrowprops=dict(arrowstyle="-"),
              bbox=bbox_props, zorder=0, va="center")

    for i, p in enumerate(wedges):
        ang = (p.theta2 - p.theta1) / 2. + p.theta1
        y = np.sin(np.deg2rad(ang))
        x = np.cos(np.deg2rad(ang))
        horizontalalignment = {-1: "right", 1: "left"}[int(np.sign(x))]
        connectionstyle = f"angle,angleA=0,angleB={ang}"
        kw["arrowprops"].update({"connectionstyle": connectionstyle})
        ax.annotate(labels[i], xy=(x, y), xytext=(1.35 * np.sign(x), 1.4 * y),
                    horizontalalignment=horizontalalignment, **kw)

    ax.set_title("Consumo energetico per sensore")

    plt.show()

def histogramChart(df,dfAR,dfARF):
    df1 = (df.T)
    df1 = df1.sum()/51
    df2 = (dfAR.T)
    df2 = df2.sum() / 51
    df3 = (dfARF.T)
    df3 = df3.sum() / 51
    sensors = df1.index.to_list()
    print(df1)
    print(df2)
    doubleDataset = {
        'Senza refactoring':df1,
        'Refactoring parziale': df2,
        'Post Refactoring': df3,
    }
    x = np.arange(len(sensors))  # the label locations
    width = 0.10  # the width of the bars
    multiplier = 0

    fig, ax = plt.subplots(layout='constrained')

    for attribute, measurement in doubleDataset.items():
        offset = width * multiplier
        rects = ax.bar(x + offset, measurement, width, label=attribute)
        #ax.bar_label(rects, padding=3)
        multiplier += 1

    for i, value in enumerate(df1):
        ax.text(x[i] - width, value + 0.1, f'{value:.0f}', ha='left', va='bottom', rotation=45)

    for i, value in enumerate(df2):
        ax.text(x[i], value + 0.1, f'{value:.0f}', ha='left', va='bottom', rotation=45)

    for i, value in enumerate(df3):
        ax.text(x[i] + width, value + 0.1, f'{value:.0f}', ha='left', va='bottom', rotation=45)

    # Add some text for labels, title and custom x-axis tick labels, etc.
    ax.set_ylabel('Energia in Watts')

    ax.set_title('Istogramma dei Valori di Energia')
    ax.set_xticks(x + width, sensors)
    ax.legend(loc='upper left', ncols=3)
    ax.set_ylim(0, max(df3) + 1)
    #plt.ylim(0, max(df1) + 1)
    plt.show()

def drawSingleHist(ax, lista,listaAR,name):
    doubleDataset = {
        'Pre refactoring': lista,
        'Post Refactoring': listaAR,
    }
    x = np.arange(len(list(range(51))))  # the label locations
    width = 0.4  # the width of the bars
    multiplier = 0

    for attribute, measurement in doubleDataset.items():
        offset = width * multiplier
        rects = ax.bar(x + offset, measurement, width, label=attribute)
        #ax.bar_label(rects, padding=3)
        multiplier += 1

    # Add some text for labels, title and custom x-axis tick labels, etc.
    ax.set_ylabel('Energia in Joule/ora')
    ax.set_xlabel(name)
    ax.set_title('Istogramma dei Valori di Energia per Run Pre e Post Refactoring')
    #ax.set_xticks(x + width, list(range(51)))
    ax.legend(loc='upper left', ncols=3)
def histogramChartEverySensor(df,dfA):
    fig, axes = plt.subplots(nrows=2, ncols=2)
    box = dict(facecolor='yellow', pad=5, alpha=0.2)

    ax1 = axes[0, 0]
    ax2 = axes[0, 1]
    ax3 = axes[1, 0]
    ax4 = axes[1, 1]
    fig, axes1 = plt.subplots(nrows=2, ncols=2)
    box = dict(facecolor='yellow', pad=5, alpha=0.2)

    ax5 = axes1[0, 0]
    ax6 = axes1[0, 1]
    ax7 = axes1[1, 0]

    drawSingleHist(ax1, df.loc["crate-container-cd"],dfA.loc["crate-container-cd"],"crate-container-cd")
    drawSingleHist(ax2, df.loc["global"],dfA.loc["global"],"global")
    drawSingleHist(ax3, df.loc["hwpc-sensor-container"],dfA.loc["hwpc-sensor-container"],"hwpc-sensor-container")
    drawSingleHist(ax4, df.loc["influx_dest"],dfA.loc["influx_dest"],"influx_dest")
    drawSingleHist(ax5, df.loc["mongo_source"],dfA.loc["mongo_source"],"mongo_source")
    drawSingleHist(ax6, df.loc["rapl"],dfA.loc["rapl"],"rapl")
    drawSingleHist(ax7, df.loc["smartwatts-formula"],dfA.loc["smartwatts-formula"],"smartwatts-formula")

    fig.subplots_adjust(hspace=0.7)
    plt.suptitle('Evoluzione del consumo di energia per ogni sensore', fontsize=16)
    plt.show()

def custom_agg(group):
    total_power = group['power'].sum()
    time_difference = group['time'].iloc[-1] - group['time'].iloc[0]
    return pd.Series({'power': total_power, 'time': time_difference})
def calcolaEnergiaeTempoMedio(df):
    df = df[df['target'] != "rapl"]
    df = df[df['target'] != "global"]
    df = df[df['target'] != "hwpc-sensor-container"]
    df = df[df['target'] != "mongo_source"]
    df = df[df['target'] != "influx_dest"]
    df = df[df['target'] != "smartwatts-formula"]

    result = df.groupby('directory').apply(custom_agg).reset_index()
    result["power"] = result['power']
    result["time"]=result['time'].dt.total_seconds() #tempo in secondi ora
    print(df)
    result["power"] = result['power'] / result['time'] #nuovo valore di potenza

    return result

def plotTimePerRun(df,dfMeta,dfAF):
    # I RISULTATI DEL GRAFICO SONO IN LINEA CON CIO CHE CI ASPETTIAMO, ALL'AUMENTARE DELL'ENERGIA AUMENTA IL TEMPO PER L'ESECUZIONE
    fig, axes = plt.subplots(nrows=1, ncols=3)
    box = dict(facecolor='yellow', pad=5, alpha=0.2)


    ax1 = axes[0]
    ax1.scatter(df['time'], df["power"], label='energia totale per Run', color='blue')
    ax1.set_title('Senza refactoring')
    ax1.set_xlabel('Tempo in secondi', bbox=box)
    ax1.set_ylabel('energia totale', bbox=box)


    ax2 = axes[1]
    ax2.scatter(dfMeta['time'], dfMeta["power"], label='energia totale per Run', color='red')
    ax2.set_title('Refactoring parziale')
    ax2.set_xlabel('Tempo in secondi', bbox=box)
    ax2.set_ylabel('energia totale', bbox=box)


    ax3 = axes[2]
    ax3.scatter(dfAF['time'], dfAF["power"], label='energia totale per Run', color='red')
    ax3.set_title('Refactoring finale')
    ax3.set_xlabel('Tempo in secondi', bbox=box)
    ax3.set_ylabel('Energia Totale', bbox=box)

    max_range1 = max(dfAF['power'])
    max_range2 = max(dfMeta['power'])
    max_range3 = max(df['power'])
    max_range=max(max_range1,max_range2,max_range3)
    max_rangeX1 = max(dfAF['time'])
    max_rangeX2 = max(dfMeta['time'])
    max_rangeX3 = max(df['time'])
    max_rangeX= max(max_rangeX1, max_rangeX2, max_rangeX3)
    ax1.set_ylim(0, max_range+ 1)
    ax2.set_ylim(0, max_range+ 1)
    ax3.set_ylim(0, max_range + 1)
    ax1.set_xlim(0, max_rangeX + 50)
    ax2.set_xlim(0, max_rangeX + 50)
    ax3.set_xlim(0, max_rangeX + 50)

    plt.suptitle('Confronto relazione tempo - energia totale per run pre e post refactoring', fontsize=16)
    plt.show()
    return max_rangeX, max_range

    '''plt.figure(figsize=(10, 6))
    plt.scatter(df['time'], df["power"], label='energia totale per Run', color='blue')
    plt.title('Relazione tra Tempo e energia totale per Run')
    plt.xlabel('Tempo')
    plt.ylabel('energia totale')
    plt.legend()
    plt.show()'''
def boxPlot(df,max_rangeX,max_range):
    plt.figure(figsize=(8, 6))
    sns.boxplot(df['power'])
    plt.title('Boxplot of Power')
    plt.ylabel('Index of Power')
    plt.ylim(5.5, max_range )
    plt.show()

    plt.figure(figsize=(8, 6))
    sns.boxplot(x='time', data=df)
    plt.title('Boxplot of time')
    plt.xlabel('Time')
    plt.xlim(100, max_rangeX )
    plt.show()
def normalizationDF(df):
    #max_value = df['time'].max()
    #df["power"] = max_value / df["time"] * df["power"]
    df["power"] = (22.152200) / df["time"] * df["power"]
    return df

def plotGeneral(df,df1,df2):
    plt.figure(figsize=(10, 6))
    box = dict(facecolor='yellow', pad=5, alpha=0.2)

    plt.plot(df["power"], color="blue", label='Pre refactoring')
    plt.plot(df1["power"], color="orange", label='Refactoring parziale')
    plt.plot(df2["power"], color="red", label='Refactoring finale')
    plt.title('Confronto consumi energetici', fontweight='bold')
    plt.ylabel('Energia totale per Run', bbox=box)
    plt.xlabel('Numero della Run', bbox=box)
    plt.legend()
    plt.show()


print(os.getcwd())
dfMain = createUniqueDataset("./rawData/crate-master")
os.chdir("..")
print(os.getcwd())
dfAfterRefactor = createUniqueDataset("./rawData/crate-cd-out")
os.chdir("..")
print(os.getcwd())
dfFirst = createUniqueDataset("./rawData/crate-mio-master-out")

#CALCOLA ENERGIA PER OGNI SENSORE (BEFORE E AFTER REFACTOR)
listaDatasetsFirst, listaSensoriFirst = calcolaEnergiaPerOgniSensore(dfFirst)
listaDatasets, listaSensori = calcolaEnergiaPerOgniSensore(dfMain)
listaDatasetsAR, listaSensoriAR = calcolaEnergiaPerOgniSensore(dfAfterRefactor)


#CALCOLA ENERGIA PER RUN PER OGNI SENSORE (BEFORE E AFTER REFACTOR)
dfMediaEnergiaPerRunPerSensoreFirst= calcolaEnergiaPerOgniRun(listaDatasetsFirst,listaSensoriFirst)


dfMediaEnergiaPerRunPerSensore = calcolaEnergiaPerOgniRun(listaDatasets,listaSensori)

dfMediaEnergiaPerRunPerSensoreAF = calcolaEnergiaPerOgniRun(listaDatasetsAR,listaSensoriAR)

#CALCOLA energia totale E TEMPO MEDIO PER OGNI RUN (BEFORE E AFTER REFACTOR)
dfEnergiaMediaTempoPerRunFirst = calcolaEnergiaeTempoMedio(dfFirst)
dfEnergiaMediaTempoPerRunFirst.rename(columns={'power': 'Index_of_Power'}, inplace=True)
dfEnergiaMediaTempoPerRun = calcolaEnergiaeTempoMedio(dfMain)
dfEnergiaMediaTempoPerRunAF = calcolaEnergiaeTempoMedio(dfAfterRefactor)
energiaTotPre=dfEnergiaMediaTempoPerRunFirst["power"].sum()
energiaTotParziale=dfEnergiaMediaTempoPerRun["power"].sum()
energiaTotPost=dfEnergiaMediaTempoPerRunAF["power"].sum()




#PLOT CHE METTE IN RELAZIONE TEMPO MEDIO E energia totale PER OGNI RUN
maxValueX, maxValue= plotTimePerRun(dfEnergiaMediaTempoPerRunFirst,dfEnergiaMediaTempoPerRun,dfEnergiaMediaTempoPerRunAF)

#2 BOX PLOT energia totale E TEMPO MEDIO

boxPlot(dfEnergiaMediaTempoPerRunFirst,maxValueX, maxValue)
boxPlot(dfEnergiaMediaTempoPerRun, maxValueX, maxValue)
boxPlot(dfEnergiaMediaTempoPerRunAF,maxValueX, maxValue)


#EVOLUZIONE energia totale PER OGNI RUN (BEFORE E AFTER REFACTOR) GRAFICO A DISPERSIONE

#EnergyEvolutionForEveryRun(dfMediaEnergiaPerRunPerSensore, dfMediaEnergiaPerRunPerSensoreAF)

#EVOLUZIONE energia totale PER OGNI RUN, 1 GRAFO PER OGNI SENSORE (BEFORE E AFTER REFACTOR)

#plotForEverySensor(dfMediaEnergiaPerRunPerSensoreFirst,dfMediaEnergiaPerRunPerSensore,dfMediaEnergiaPerRunPerSensoreAF)
#plotGeneral(dfEnergiaMediaTempoPerRunFirst,dfEnergiaMediaTempoPerRun,dfEnergiaMediaTempoPerRunAF)

#DIAGRAMMA A TORTA CHE MOSTRA L'energia totale SUDDIVISA PER OGNI SENSORE
#piechartEnergyTot(dfMediaEnergiaPerRunPerSensore)

#piechartEnergyTot(dfMediaEnergiaPerRunPerSensoreAF)
print("ciao")
#ISTOGRAMMA CHE MOSTRA L'energia totale PER RUN DI OGNI SENSORE (BEFORE E AFTER REFACTOR)
#histogramChart(dfMediaEnergiaPerRunPerSensoreFirst,dfMediaEnergiaPerRunPerSensore,dfMediaEnergiaPerRunPerSensoreAF)

#ISTOGRAMMA CHE MOSTRA L'energia totale PER RUN, 1 GRAFO PER SENSORE (BEFORE E AFTER REFACTOR)
#histogramChartEverySensor(dfMediaEnergiaPerRunPerSensore,dfMediaEnergiaPerRunPerSensoreAF)







