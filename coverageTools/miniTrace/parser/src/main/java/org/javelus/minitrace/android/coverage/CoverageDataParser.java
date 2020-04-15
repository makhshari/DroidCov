package org.javelus.minitrace.android.coverage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.javelus.minitrace.android.MemberMeta;
import org.javelus.minitrace.android.Utils;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.iface.instruction.Instruction;

public class CoverageDataParser {

    // private Map<MemberMeta, String> methodMetaToData = new HashMap<MemberMeta, String>();
    private Map<String, ClassData> classData = new HashMap<String, ClassData>();
    private Map<MemberMeta, MethodData> methodData = new HashMap<MemberMeta, MethodData>();

    public CoverageDataParser() {
    }

    public void loadApk(File file) throws IOException {
        System.out.println("INFO: Loading dex file: " + file);
        MultiDexContainer<? extends DexBackedDexFile> container = DexFileFactory.loadDexContainer(file, Opcodes.getDefault());
        for (String entryName : container.getDexEntryNames()) {
            DexBackedDexFile dexFile = container.getEntry(entryName);
            loadApk(dexFile);
        }
    }

    private ClassData createClassData(String className, ClassDef classDef) {
        ClassData data = this.classData.get(className);
        if (data != null) {
            System.out.println("WARNING: Ignore duplicated class data for class " + className);
            return null;
        }
        data = new ClassData(classDef);
        this.classData.put(className, data);
        return data;
    }

    /**
     * Load all classes and methods into the memory.
     * @param dexFile
     */
    public void loadApk(DexFile dexFile) {
        for (ClassDef classDef: dexFile.getClasses()) {
            ClassData classData = createClassData(classDef.getType(), classDef);
            if (classData == null) {
                continue;
            }
            if (!isPackageIncluded(typeToClassName(classDef.getType()))) {
                continue;
            }
            for (Method method:classDef.getMethods()) {
                MemberMeta mm = new MemberMeta(Utils.binaryNameToName(method.getDefiningClass()), method.getName(), 
                        Utils.getDescriptor(method.getParameterTypes(), method.getReturnType()));
                MethodData md = new MethodData(mm, method);
                classData.addMethodData(md);
                if (methodData.put(mm, md) != null) {
                    System.out.println("WARNING: duplicated method " + mm);
                    methodData.remove(mm);
                }
            }
        }
    }

    private static String typeToClassName(String type) {
        if (type.startsWith("L") && type.endsWith(";")) {
            return type.substring(1, type.length() - 1).replace('/', '.');
        }
        return type;
    }

    public void updateCoverage(Map<MemberMeta, String> methodMetaToData) {
        for (Entry<MemberMeta, String> entry : methodMetaToData.entrySet()) {
            MemberMeta mm = entry.getKey();
            if (!isPackageIncluded(mm.getClassName())) {
                continue;
            }

            String newData = entry.getValue();
            MethodData md = methodData.get(mm);
            if (md == null) {
                if (verbose) System.err.println("WARNING: No method " + mm);
                continue;
            }

            Method m = md.getMethod();
            if (m.getImplementation() == null) {
                if (verbose) System.err.println("WARNING: No implementation for method " + mm);
                continue;
            }
            String actualData = newData;
            {
                int pc = 0;
                for (Instruction insn:m.getImplementation().getInstructions()) {
                    pc += insn.getCodeUnits();
                }
                if (actualData.indexOf('_') != -1) {
                    for (String temp : actualData.split("_")) {
                        if (pc == temp.length()) {
                            actualData = temp;
                            break;
                        }
                    }
                }
                if (pc != actualData.length()) {
                    if (verbose) System.err.println("WARNING: Inconsistency bytecode length of " + mm);
                    continue;
                }
            }
            int total = 0;
            int pc = 0;
            byte[] data = md.getData();
            for (Instruction insn:m.getImplementation().getInstructions()) {
                if (actualData.charAt(pc) == '1') {
                    data[total] = 1;
                }
                total++;
                pc += insn.getCodeUnits();
            }
            if (pc != actualData.length()) {
                throw new RuntimeException("Sanity check failed!");
            }
            md.update();
        }
    }

    private boolean isPackageIncluded(String packageName) {
        boolean included = isPackageIncludedInternal(packageName);
        // System.out.format("Check package name %s, result %s\n", packageName, (included ? "included" : "excluded"));
        return included;
    }

    private boolean isPackageIncludedInternal(String packageName) {
        if (whiteList != null && blackList != null) {
            for (String pkg : whiteList) {
                if (packageName.startsWith(pkg)) {
                    return true;
                }
            }
            for (String pkg : blackList) {
                if (packageName.startsWith(pkg)) {
                    return false;
                }
            }
            return true;
        }

        if (whiteList != null) {
            for (String pkg : whiteList) {
                if (packageName.startsWith(pkg)) {
                    return true;
                }
            }
            return false;
        }

        if (blackList != null) {
            for (String pkg : blackList) {
                if (packageName.startsWith(pkg)) {
                    return false;
                }
            }
            return true;
        }

        return true;

    }

    public void histagram() {
        int [] counters = new int[11]; // 0, 10%, ..., 100%
        for (ClassData cd : this.classData.values()) {
            for (MethodData md : cd.getAllMethodData()) {
                if (md.isAbstractOrNative()) {
                    continue;
                }
                float c = md.getCoverage() * 100;
                int ic = (int) c;
                counters[ic / 10] = counters[ic / 10] + 1;
            }
        }
        System.out.println();
        for (int i = 0; i < 11; i++) {
            System.out.format("%3d%% %d\n", i * 10, counters[i]);
        }
    }

    public void output(boolean verbose) {
        if (this.classData.isEmpty()) {
            System.err.println("ERROR: No data!");
            return;
        }
        int maxName = -1;
        for (String name : classData.keySet()) {
            if (name.length() > maxName) {
                maxName = name.length();
            }
        }

        List<ClassData> data = new ArrayList<ClassData>(this.classData.values());
        Collections.sort(data, new Comparator<ClassData>(){

            @Override
            public int compare(ClassData o1, ClassData o2) {
                return o1.getClassName().compareTo(o2.getClassName());
            }

        });
        String format = String.format("%%-%ds:\t%%d\t%%d\t%%d\t%%d\t%%d\n", maxName);
        int totalMethods = 0;
        int totalConcreteMethods = 0;
        int totalCoveredMethods = 0;
        int totalInsns = 0;
        int totalCoveredInsns = 0;
        if (verbose) System.out.format(String.format("%%-%ds:\t%%s\t%%s\t%%s\t%%s\t%%s\n", maxName),
                "Name", "Meth", 
                "ConM", "CovM",
                "Insn", "CovI");
        for (ClassData cd : data) {
            int classMethods = cd.getAllMethodData().size();
            int classConcreteMethods = 0;
            int classCoveredMethods = 0;
            int classInsns = 0;
            int classCoveredInsns = 0;
            for (MethodData md : cd.getAllMethodData()) {
                if (md.isAbstractOrNative()) {
                    continue;
                }
                classConcreteMethods++;
                if (md.isCovered()) {
                    classCoveredMethods++;
                }
                classInsns += md.getInsnSize();
                classCoveredInsns += md.getCovered();
            }
            if (verbose) {
                System.out.format(format, cd.getClassName(), classMethods, 
                        classConcreteMethods, classCoveredMethods,
                        classInsns, classCoveredInsns);
            }

            totalMethods += classMethods;
            totalConcreteMethods += classConcreteMethods;
            totalCoveredMethods += classCoveredMethods;
            totalInsns += classInsns;
            totalCoveredInsns += classCoveredInsns;
        }
        if (verbose) {
            System.out.format(format, "", totalMethods, 
                    totalConcreteMethods, totalCoveredMethods,
                    totalInsns, totalCoveredInsns);
        } else {
            System.out.format("%d\t%d\t%d\t%d\t%d\t%f\t%f\t%f\n",
                    totalMethods, totalConcreteMethods, totalCoveredMethods, totalInsns, totalCoveredInsns,
                    safeDiv(totalCoveredMethods, totalMethods),
                    safeDiv(totalCoveredMethods, totalConcreteMethods),
                    safeDiv(totalCoveredInsns, totalInsns));
        }
    }

    static float safeDiv(int a, int b) {
        if (b == 0) {
            return Float.NaN;
        }
        return a / (float) b;
    }

    private static boolean verbose;
    private static List<File> apkFiles = new ArrayList<File>();
    private static List<File> covDataFiles = new ArrayList<File>();
    private static List<File> baseCovDataFiles = new ArrayList<File>();
    private static Set<String> whiteList;
    private static Set<String> blackList;
    private static DataParser dataParser;

    public static void main(String[] args) throws IOException {
        processCommandLine(args);
        CoverageDataParser dc = new CoverageDataParser();

        for (File apk : apkFiles) {
            dc.loadApk(apk);
        }

        for (File cov : covDataFiles) {
            List<Data> data = loadData(cov);
            System.out.println("Coverage data after adding " + cov);
            for (Data d : data) {
                dc.updateCoverage(d.data);
                System.out.format("%d\t%d\t%s\t", d.timestamp, d.pid, d.type);
                dc.output(false);
            }
        }

        if (verbose) dc.output(verbose);
        dc.histagram();
        
        
        if (!baseCovDataFiles.isEmpty()) {
            CoverageDataParser base = new CoverageDataParser();
            for (File apk : apkFiles) {
                base.loadApk(apk);
            }
            for (File cov : baseCovDataFiles) {
                List<Data> data = loadData(cov);
                System.out.println("Coverage data after adding " + cov);
                for (Data d : data) {
                    base.updateCoverage(d.data);
                    System.out.format("%d\t%d\t%s\t", d.timestamp, d.pid, d.type);
                    base.output(false);
                }
            }
            if (verbose) base.output(verbose);
            base.histagram();
            
            dc.outputDiff(base);
        }
    }

    private void outputDiff(CoverageDataParser base) {
        if (this.classData.isEmpty()) {
            return;
        }
        if (base.classData.isEmpty()) {
            return;
        }

        int thisTotalMethods = 0;
        int thisTotalConcreteMethods = 0;
        int thisTotalCoveredMethods = 0;
        int thisTotalInsns = 0;
        int thisTotalCoveredInsns = 0;

        int baseTotalCoveredMethods = 0;
        int baseTotalCoveredInsns = 0;
        
        int commonTotalCoveredMethods = 0;
        int commonTotalCoveredInsns = 0;
        for (ClassData cd : this.classData.values()) {
            ClassData baseCD = base.classData.get(cd.getClassName());
            if (baseCD == null) {
                throw new RuntimeException("Unmatched APK..");
            }
            int classMethods = cd.getAllMethodData().size();
            int classInsns = 0;
            int classConcreteMethods = 0;
            
            int thisClassCoveredMethods = 0;
            int thisClassCoveredInsns = 0;
            
            int baseClassCoveredMethods = 0;
            int baseClassCoveredInsns = 0;
            int commonClassCoveredMethods = 0;
            int commonClassCoveredInsns = 0;
            for (MethodData md : cd.getAllMethodData()) {
                if (md.isAbstractOrNative()) {
                    continue;
                }
                MethodData baseMD = baseCD.getMethodData(md.getMeta());
                classConcreteMethods++;
                if (md.isCovered()) {
                    thisClassCoveredMethods++;
                    if (baseMD.isCovered()) {
                        commonClassCoveredMethods++;
                    }
                }
                if (baseMD.isCovered()) {
                    baseClassCoveredMethods++;
                }
                classInsns += md.getInsnSize();
                thisClassCoveredInsns += md.getCovered();
                baseClassCoveredInsns += baseMD.getCovered();
                commonClassCoveredInsns += md.getCommonCovered(baseMD);
            }

            thisTotalMethods += classMethods;
            thisTotalConcreteMethods += classConcreteMethods;
            thisTotalCoveredMethods += thisClassCoveredMethods;
            thisTotalInsns += classInsns;
            thisTotalCoveredInsns += thisClassCoveredInsns;

            baseTotalCoveredMethods += baseClassCoveredMethods;
            baseTotalCoveredInsns += baseClassCoveredInsns;
            commonTotalCoveredMethods += commonClassCoveredMethods;
            commonTotalCoveredInsns += commonClassCoveredInsns;
        }
        System.out.format("%d\t%d\t%d\t%d\t%d\t%f\t%f\t%f"
                + "\n"
                + "%d\t%d\t%d\t%d\t%d\t%f\t%f\t%f"
                + "\n"
                + "%d\t%d\t%d\t%d\t%d\t%f\t%f\t%f"
                + "\n"
                + "%d\t%d\t%d\t%d\t%d\t%f\t%f\t%f"
                + "\n"
                + "%d\t%d\t%d\t%d\t%d\t%f\t%f\t%f"
                + "\n",
                thisTotalMethods, thisTotalConcreteMethods, thisTotalCoveredMethods, thisTotalInsns, thisTotalCoveredInsns,
                safeDiv(thisTotalCoveredMethods, thisTotalMethods),
                safeDiv(thisTotalCoveredMethods, thisTotalConcreteMethods),
                safeDiv(thisTotalCoveredInsns, thisTotalInsns),
                thisTotalMethods, thisTotalConcreteMethods, baseTotalCoveredMethods, thisTotalInsns, baseTotalCoveredInsns,
                safeDiv(baseTotalCoveredMethods, thisTotalMethods),
                safeDiv(baseTotalCoveredMethods, thisTotalConcreteMethods),
                safeDiv(baseTotalCoveredInsns, thisTotalInsns),
                thisTotalMethods, thisTotalConcreteMethods, commonTotalCoveredMethods, thisTotalInsns, commonTotalCoveredInsns,
                safeDiv(commonTotalCoveredMethods, thisTotalMethods),
                safeDiv(commonTotalCoveredMethods, thisTotalConcreteMethods),
                safeDiv(commonTotalCoveredInsns, thisTotalInsns),
                thisTotalMethods, thisTotalConcreteMethods, thisTotalCoveredMethods - commonTotalCoveredMethods, thisTotalInsns, thisTotalCoveredInsns - commonTotalCoveredInsns,
                safeDiv(thisTotalCoveredMethods - commonTotalCoveredMethods, thisTotalMethods),
                safeDiv(thisTotalCoveredMethods - commonTotalCoveredMethods, thisTotalConcreteMethods),
                safeDiv(thisTotalCoveredInsns - commonTotalCoveredInsns, thisTotalInsns),
                thisTotalMethods, thisTotalConcreteMethods, baseTotalCoveredMethods - commonTotalCoveredMethods, thisTotalInsns, thisTotalCoveredInsns - commonTotalCoveredInsns,
                safeDiv(baseTotalCoveredMethods - commonTotalCoveredMethods, thisTotalMethods),
                safeDiv(baseTotalCoveredMethods - commonTotalCoveredMethods, thisTotalConcreteMethods),
                safeDiv(baseTotalCoveredInsns - commonTotalCoveredInsns, thisTotalInsns)
                
                );
    }

    private static List<Data> loadData(File cov) throws IOException {
        return dataParser.parse(cov);
    }

    private static void processCommandLine(String[] args) throws IOException {
        int i = 0;
        while (i < args.length) {
            String opt = args[i++];
            if (opt.equals("-inpkg")) {
                addWhiteList(args[i++]);
            } else if (opt.equals("-expkg")) {
                addBlackList(args[i++]);
            } else if (opt.equals("-apk")) {
                addApk(args[i++]);
            } else if (opt.equals("-v")) {
                verbose = true;
            } else if (opt.equals("-cov")) {
                addCovData(args[i++]);
            } else if (opt.equals("-base")) {
                addBaseCovData(args[i++]);
            } else if (opt.equals("-covlist")) {
                addCovDataList(args[i++]);
            } else if (opt.equals("-moni")) {
                addMoniDataDirectory(args[i++]);
                dataParser = new MoniDataParser();
            } else {
                throw new RuntimeException("Unknown option: " + opt);
            }
        }

        if (dataParser == null) {
            dataParser = new IncrementalDataParser();
        }

        if (apkFiles.isEmpty()) {
            throw new RuntimeException("Please specify a apk file via -apk <apk-file>");
        }

        if (covDataFiles.isEmpty()) {
            throw new RuntimeException("Please specify a coverage data file via -cov <cov-data-file>");
        }
    }

    private static void addBaseCovData(String data) {
        File file = new File(data);
        assureFileExist(file);
        baseCovDataFiles.add(file);
    }

    private static void addMoniDataDirectory(String string) {
        File dataDir = new File(string);
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            throw new RuntimeException("No such directory " + string);
        }
        File[] files = dataDir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("_coverage.dat");
            }

        });
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                String n1 = o1.getName();
                String n2 = o2.getName();
                int t1 = 0;
                int t2 = 0;

                try {
                    int index = n1.indexOf("_");
                    t1 = Integer.parseInt(n1.substring(0, index));
                } catch (NumberFormatException e) {}
                try {
                    int index = n2.indexOf("_");
                    t2 = Integer.parseInt(n2.substring(0, index));
                } catch (NumberFormatException e) {}
                return t1 - t2;
            }
        });

        for (File f : files) {
            covDataFiles.add(f);
        }
    }

    static void assureFileExist(File file) {
        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("Not a regular file " + file.getAbsolutePath());
        }
    }

    private static void addCovDataList(String listFile) throws IOException {
        File list = new File(listFile);
        File parent = list.getParentFile();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(list)))) {
            String line;
            while ((line = br.readLine()) != null) {
                File covData = new File(parent, line);
                assureFileExist(covData);
                covDataFiles.add(covData);
            }
        }
    }

    private static void addCovData(String data) {
        File file = new File(data);
        assureFileExist(file);
        covDataFiles.add(file);
    }

    private static void addApk(String apk) {
        File file = new File(apk);
        assureFileExist(file);
        apkFiles.add(file);
    }

    private static void addWhiteList(String pkg) {
        if (whiteList == null) {
            whiteList = new HashSet<>();
        }
        whiteList.add(pkg);
    }
    private static void addBlackList(String pkg) {
        if (blackList == null) {
            blackList = new HashSet<>();
        }
        blackList.add(pkg);
    }
}
