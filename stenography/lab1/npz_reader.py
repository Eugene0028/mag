import os
from PIL import Image

# Указываем твою папку
input_root = "nowildfire" 
output_dir = "container3bmp"

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

count = 0
# Берем список файлов и фильтруем только картинки
files = [f for f in os.listdir(input_root) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]

for file in files:
    if count >= 100:
        break
        
    full_path = os.path.join(input_root, file)
    
    try:
        img = Image.open(full_path).convert('L') # В 8-бит серый
        img = img.resize((512, 512))            # Размер строго 512x512
        
        img.save(os.path.join(output_dir, f'sat_{count}.bmp'))
        count += 1
    except Exception as e:
        print(f"Ошибка с файлом {file}: {e}")

print(f"Готово! 100 спутниковых снимков лежат в {output_dir}")
