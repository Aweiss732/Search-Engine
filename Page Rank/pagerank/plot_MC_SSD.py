import matplotlib.pyplot as plt

N = [17478, 34956, 52435, 69912, 87390, 174780]


MC1 = [0.000003101, 0.000004120, 0.000000831, 0.000001999, 0.000000373, 0.000000588]
MC2 = [0.000003072, 0.000001995, 0.000001784, 0.000000838, 0.000001212, 0.000000352]
MC4 = [0.000001823, 0.000000534, 0.000000836, 0.000000935, 0.000000197, 0.000000204]
MC5 = [0.000002121, 0.000001041, 0.000000269, 0.000000460, 0.000000328, 0.000000096]


plt.plot(N, MC1, marker='o', label='MC1 SSD')
plt.plot(N, MC2, marker='o', label='MC2 SSD')
plt.plot(N, MC4, marker='o', label='MC4 SSD')
plt.plot(N, MC5, marker='o', label='MC5 SSD')


plt.xlabel('N')
plt.ylabel('SSD Score')
plt.title('SSD Scores vs. N')

plt.legend()

plt.grid(True)

plt.show()
